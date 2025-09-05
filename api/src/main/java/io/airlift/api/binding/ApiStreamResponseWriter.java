package io.airlift.api.binding;

import io.airlift.api.ApiStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiByteStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiOutputStreamResponse;
import io.airlift.api.ApiStreamResponse.ApiTextStreamResponse;
import io.airlift.api.validation.ValidationContext;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static java.nio.charset.StandardCharsets.UTF_8;

@Provider
public class ApiStreamResponseWriter
        implements MessageBodyWriter<ApiStreamResponse>
{
    private final ValidationContext validationContext = new ValidationContext();

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType)
    {
        return ApiStreamResponse.class.isAssignableFrom(type);
    }

    @SuppressWarnings("resource")
    @Override
    public void writeTo(ApiStreamResponse apiStreamResponse, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException
    {
        MediaType streamingMediaType = validationContext.streamingResponseMediaType(genericType);
        httpHeaders.putSingle("Content-Type", streamingMediaType.toString());
        switch (apiStreamResponse) {
            case ApiTextStreamResponse<?> textStreamResponse -> {
                Writer writer = new BufferedWriter(new OutputStreamWriter(entityStream, UTF_8));
                textStreamResponse.stream().transferTo(writer);
                writer.flush();
            }

            case ApiByteStreamResponse<?> byteStreamResponse -> byteStreamResponse.stream().transferTo(entityStream);

            case ApiOutputStreamResponse<?> outputStreamResponse -> outputStreamResponse.stream().accept(entityStream);
        }
    }
}
