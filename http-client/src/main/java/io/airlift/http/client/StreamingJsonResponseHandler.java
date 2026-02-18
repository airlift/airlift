package io.airlift.http.client;

import com.google.common.io.CountingInputStream;
import com.google.common.net.MediaType;
import io.airlift.json.JsonCodec;

import java.io.InputStream;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseStream;
import static java.util.Objects.requireNonNull;

/**
 * StreamingJsonResponseHandler is a {@link ResponseHandler} that creates a JSON entity from the response bytes when Content-Type
 * is application/json. In contrast to {@link FullJsonResponseHandler} it always reads data directly from an InputStream
 * exposed by the Jetty HttpClient. Once Jetty is upgraded to 12.1.7 this will use a new listener that exposes an InputStream
 * reading directly from Jetty's pooled response buffers, thus avoiding data copying and materialization. This makes it harder
 * to diagnose errors during decoding as the response bytes are not buffered and available for inspection.
 */
public class StreamingJsonResponseHandler<T>
        implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    private final JsonCodec<T> codec;

    public StreamingJsonResponseHandler(JsonCodec<T> codec)
    {
        this.codec = requireNonNull(codec, "codec is null");
    }

    public static <T> StreamingJsonResponseHandler<T> streamingJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new StreamingJsonResponseHandler<>(jsonCodec);
    }

    @Override
    public JsonResponse<T> handleException(Request request, Exception exception)
            throws RuntimeException
    {
        return new JsonResponse.Exception<>(request, -1, exception);
    }

    @Override
    public JsonResponse<T> handle(Request request, Response response)
            throws RuntimeException
    {
        int statusCode = response.getStatusCode();

        try {
            String contentType = response.getHeader(CONTENT_TYPE);
            if (contentType == null) {
                return new JsonResponse.NonJsonBytes<>(
                        request,
                        statusCode,
                        response.getHeaders(),
                        getResponseBytes(request, response),
                        new UnexpectedResponseException("Content-Type is not set for response", request, response));
            }

            if (MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
                try (InputStream stream = getResponseStream(response); CountingInputStream countingInputStream = new CountingInputStream(stream)) {
                    return new JsonResponse.JsonValue<>(request, statusCode, response.getHeaders(), codec.fromJson(countingInputStream), countingInputStream.getCount());
                }
            }

            return new JsonResponse.NonJsonBytes<>(
                    request,
                    statusCode,
                    response.getHeaders(),
                    getResponseBytes(request, response),
                    new UnexpectedResponseException("Expected server to response with application/json but got " + contentType, request, response));
        }
        catch (Exception e) {
            return new JsonResponse.Exception<>(request, statusCode, e);
        }
    }
}
