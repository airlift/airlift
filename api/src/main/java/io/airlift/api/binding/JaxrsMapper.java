package io.airlift.api.binding;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import io.airlift.api.ApiPatch;
import io.airlift.jaxrs.JsonMapper;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS;
import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

@Priority(0)
@Provider
@Consumes({MediaType.APPLICATION_JSON, "text/json"})
@Produces({MediaType.APPLICATION_JSON, "text/json"})
public class JaxrsMapper
        implements MessageBodyReader<Object>, MessageBodyWriter<Object>
{
    private static final Pattern BAD_POLY_TYPE = Pattern.compile("(No binding was made for property name \")(.*)(\" and value \")(.*)(\". Double check the addBinding\\(\\) or addPermittedSubClassBindings\\(\\).)");

    private final JsonMapper jsonMapper;
    private final PatchFieldsBuilder patchFieldsBuilder;

    @Inject
    public JaxrsMapper(ObjectMapper objectMapper, PatchFieldsBuilder patchFieldsBuilder)
    {
        this.patchFieldsBuilder = requireNonNull(patchFieldsBuilder, "patchFieldsBuilder is null");
        jsonMapper = new JsonMapper(objectMapper);

        objectMapper.enable(FAIL_ON_NULL_FOR_PRIMITIVES);
        objectMapper.enable(FAIL_ON_NULL_CREATOR_PROPERTIES);
        objectMapper.enable(FAIL_ON_NUMBERS_FOR_ENUMS);
        objectMapper.disable(FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return JaxrsUtil.isApiResource(type) || ApiPatch.class.isAssignableFrom(type);
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return jsonMapper.isWriteable(type, genericType, annotations, mediaType);
    }

    @Override
    public void writeTo(Object o, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException
    {
        jsonMapper.writeTo(o, type, genericType, annotations, mediaType, httpHeaders, entityStream);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws WebApplicationException
    {
        try {
            if (ApiPatch.class.isAssignableFrom(type)) {
                return patchFieldsBuilder.buildPatchFields(entityStream);
            }

            return jsonMapper.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
        }
        catch (IOException e) {
            return mapException(e);
        }
    }

    static <T> T mapException(Exception e)
    {
        throw switch (Throwables.getRootCause(e)) {
            case UnrecognizedPropertyException propertyException -> badRequest("Field does not exist: " + propertyException.getPropertyName());

            case JsonParseException jsonParseException -> badRequest(jsonParseException.getOriginalMessage());

            case InvalidTypeIdException invalidTypeIdException -> badPolyException("typeKey", invalidTypeIdException.getTypeId());

            case InvalidNullException nullException -> badRequest("Null fields are not allowed: %s".formatted(nullException.getPropertyName().getSimpleName()));

            case IllegalArgumentException exception -> {
                String message = Optional.ofNullable(exception.getMessage()).orElse("Unknown error");
                yield maybeBadPoly(message).orElseGet(() -> badRequest(message));
            }

            case MismatchedInputException inputException -> {
                String typeName = (inputException.getTargetType() != null) ? inputException.getTargetType().getSimpleName() : "unknown";
                if (inputException.getMessage() != null) {
                    if ((inputException.getMessage().contains("FAIL_ON_NULL_CREATOR_PROPERTIES") || inputException.getMessage().contains("FAIL_ON_NULL_FOR_PRIMITIVES"))) {
                        yield badRequest("One or more fields in %s were null".formatted(typeName));
                    }
                    if (inputException.getMessage().contains("Cannot deserialize value of type") || inputException.getMessage().contains("Cannot construct instance of")) {
                        yield badRequest("Could not parse entity: %s".formatted(typeName));
                    }
                }

                yield badRequest(inputException.getMessage());
            }

            default -> badRequest("Unrecognized entity");
        };
    }

    private static Optional<RuntimeException> maybeBadPoly(String message)
    {
        // not ideal - it's a bit late to change Airlift. So, do this message string extraction for now
        Matcher matcher = BAD_POLY_TYPE.matcher(message);
        if (matcher.matches() && (matcher.groupCount() > 4)) {
            String field = matcher.group(2);
            String value = matcher.group(4);

            return Optional.of(badPolyException(field, value));
        }

        return Optional.empty();
    }

    private static RuntimeException badPolyException(String field, String value)
    {
        return badRequest("Unknown %s. %s is not a valid value".formatted(field, value));
    }
}
