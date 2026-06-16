package io.airlift.jaxrs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonYAMLProvider;
import com.google.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static com.fasterxml.jackson.jakarta.rs.cfg.JakartaRSFeature.ADD_NO_SNIFF_HEADER;

@Provider
@Consumes({"application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml"})
// Server quality (qs) below the JSON provider's so JSON wins when a client sends "*/*"
// (no Accept header) or otherwise expresses no preference between JSON and YAML.
@Produces("application/yaml;qs=0.5")
public class JaxRsYamlMapper
        extends JacksonYAMLProvider
{
    private YamlParsingConfig parsingConfig = YamlParsingConfig.unbounded();

    public JaxRsYamlMapper()
    {
        super(new YAMLMapper());
        enable(ADD_NO_SNIFF_HEADER);
    }

    @Inject(optional = true)
    public void setYamlMapper(YAMLMapper yamlMapper)
    {
        setMapper(yamlMapper);
    }

    @Inject(optional = true)
    public void setParsingConfig(YamlParsingConfig parsingConfig)
    {
        this.parsingConfig = parsingConfig;
    }

    /**
     * Throws YamlParsingException only when Jakarta-RS container calls to deserialize given YAML value.
     * Distinguishes between:
     * - JsonProcessingException due to Jakarta-RS deserialization
     * - JsonProcessingException due to operation happening in resource body
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException
    {
        InputStream stream = parsingConfig.maxPayloadSize()
                .map(size -> (InputStream) new LimitInputStream(entityStream, size.toBytes()))
                .orElse(entityStream);
        try {
            return super.readFrom(type, genericType, annotations, mediaType, httpHeaders, stream);
        }
        catch (IOException e) {
            // SnakeYAML wraps the underlying IOException
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof PayloadTooLargeException payloadTooLarge) {
                    throw payloadTooLarge;
                }
            }
            if (!(e instanceof JsonProcessingException) && !(e instanceof EOFException)) {
                throw e;
            }
            throw new YamlParsingException(e);
        }
    }

    @Override
    protected boolean hasMatchingMediaType(MediaType mediaType)
    {
        if (mediaType == null) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        // JacksonYAMLProvider's default only matches "yaml" or "*+yaml"; we also accept "x-yaml"
        // for compatibility with legacy clients.
        return "yaml".equalsIgnoreCase(subtype)
                || "x-yaml".equalsIgnoreCase(subtype)
                || subtype.endsWith("+yaml");
    }

    @Override
    protected boolean hasMatchingMediaTypeForWriting(MediaType mediaType)
    {
        // Only write YAML when the chosen response media type is explicitly YAML.
        // Without this override, a resource method without an explicit @Produces would
        // pick YAML over JSON whenever a wildcard Accept (or no Accept) reached the server.
        if (mediaType == null) {
            return false;
        }
        String subtype = mediaType.getSubtype();
        return "yaml".equalsIgnoreCase(subtype)
                || "x-yaml".equalsIgnoreCase(subtype)
                || subtype.endsWith("+yaml");
    }
}
