package io.airlift.http.client;

import com.google.common.io.CountingInputStream;
import com.google.common.net.MediaType;
import io.airlift.json.JsonCodec;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseBytes;
import static io.airlift.http.client.ResponseHandlerUtils.getResponseStream;
import static java.util.Objects.requireNonNull;

public class StreamingJsonResponseHandler<T>
        implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    private static final MediaType MEDIA_TYPE_JSON = MediaType.create("application", "json");

    private final JsonCodec<T> codec;

    public StreamingJsonResponseHandler(JsonCodec<T> codec)
    {
        this.codec = requireNonNull(codec, "codec is null");
    }

    public static <T> StreamingJsonResponseHandler<T> createStreamingJsonResponseHandler(JsonCodec<T> jsonCodec)
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
            if (!MediaType.parse(contentType).is(MEDIA_TYPE_JSON)) {
                return new JsonResponse.NonJsonBytes<>(
                        request,
                        statusCode,
                        response.getHeaders(),
                        getResponseBytes(request, response),
                        new UnexpectedResponseException("Expected application/json response from server but got " + contentType, request, response));
            }
            try (CountingInputStream countingInputStream = new CountingInputStream(getResponseStream(response))) {
                return new JsonResponse.JsonValue<>(request, statusCode, response.getHeaders(), codec.fromJson(countingInputStream), countingInputStream.getCount());
            }
        }
        catch (Exception e) {
            return new JsonResponse.Exception<>(request, statusCode, e);
        }
    }
}
