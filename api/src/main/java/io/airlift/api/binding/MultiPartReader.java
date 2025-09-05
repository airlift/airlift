package io.airlift.api.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiMultiPart.ApiMultiPartFormWithResource;
import io.airlift.api.ApiMultiPart.ItemInput;
import io.airlift.api.validation.ValidationContext;
import io.airlift.log.Logger;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.RequestContext;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.glassfish.jersey.server.ContainerRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

class MultiPartReader
        implements Function<ContainerRequest, Object>
{
    private static final Logger log = Logger.get(MultiPartReader.class);

    private static final ValidationContext validationContext = new ValidationContext();

    private final Class<?> resourceType;
    private final ObjectMapper objectMapper;
    private final boolean resourceIsFirstItem;

    MultiPartReader(Type type, ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        resourceType = validationContext.extractGenericParameterAsClass(type, 0);
        checkArgument(resourceType.isRecord(), "type is not a record: %s", resourceType);
        resourceIsFirstItem = TypeToken.of(type).isSubtypeOf(ApiMultiPartFormWithResource.class);
    }

    @Override
    public Object apply(ContainerRequest containerRequest)
    {
        try {
            JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
            FileItemInputIterator itemIterator = upload.getItemIterator(map(containerRequest));

            if (resourceIsFirstItem && itemIterator.hasNext()) {
                FileItemInput itemInput = itemIterator.next();
                Object resource = objectMapper.readValue(itemInput.getInputStream(), resourceType);
                return new ApiMultiPartFormWithResource<>(resource, itemInputIterator(itemIterator));
            }

            return new ApiMultiPartForm<>(itemInputIterator(itemIterator));
        }
        catch (IOException e) {
            log.error(e, "Could not read multipart form");
            throw badRequest("Could not read multipart form. Details: %s" + e.getMessage());
        }
    }

    private Iterator<ItemInput> itemInputIterator(FileItemInputIterator itemIterator)
    {
        return new Iterator<>()
        {
            @Override
            public boolean hasNext()
            {
                try {
                    return itemIterator.hasNext();
                }
                catch (IOException e) {
                    log.error(e, "Could not check next item");
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public ItemInput next()
            {
                try {
                    return itemInput(itemIterator.next());
                }
                catch (IOException e) {
                    log.error(e, "Could not read next item");
                    throw new UncheckedIOException(e);
                }
            }
        };
    }

    private ItemInput itemInput(FileItemInput fileItemInput)
    {
        return new ItemInput()
        {
            @Override
            public String contentType()
            {
                return fileItemInput.getContentType();
            }

            @Override
            public Optional<String> header(String name)
            {
                return Optional.ofNullable(fileItemInput.getHeaders().getHeader(name));
            }

            @Override
            public Collection<String> headerNames()
            {
                return ImmutableSet.copyOf(fileItemInput.getHeaders().getHeaderNames());
            }

            @Override
            public InputStream inputStream()
                    throws IOException
            {
                return fileItemInput.getInputStream();
            }

            @Override
            public String name()
            {
                return fileItemInput.getFieldName();
            }

            @Override
            public Optional<String> fileName()
            {
                return Optional.ofNullable(fileItemInput.getName());
            }
        };
    }

    private RequestContext map(ContainerRequest containerRequest)
    {
        return new RequestContext()
        {
            @Override
            public String getCharacterEncoding()
            {
                return Charset.defaultCharset().name();
            }

            @Override
            public Charset getCharset()
            {
                return Charset.defaultCharset();
            }

            @Override
            public long getContentLength()
            {
                return containerRequest.getLength();
            }

            @Override
            public String getContentType()
            {
                return containerRequest.getMediaType().toString();
            }

            @Override
            public InputStream getInputStream()
            {
                return containerRequest.getEntityStream();
            }
        };
    }
}
