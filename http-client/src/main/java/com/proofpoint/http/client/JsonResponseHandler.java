package com.proofpoint.http.client;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;

public class JsonResponseHandler<T> implements ResponseHandler<T, RuntimeException>
{
    public static <T> JsonResponseHandler<T> createJsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        return new JsonResponseHandler<T>(jsonCodec);
    }

    private final JsonCodec<T> jsonCodec;

    private JsonResponseHandler(JsonCodec<T> jsonCodec)
    {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public RuntimeException handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            URI uri = request.getUri();
            try {
                uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), "/", null, null);
            }
            catch (Exception e) {
            }
            return new RuntimeException("Coordinator refused connection: " + uri);
        }
        return Throwables.propagate(exception);
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (response.getStatusCode() / 100 != 2) {
            throw new UnexpectedResponseException(request, response);
        }
        String contentType = response.getHeader("Content-Type");
        if (!"application/json".equals(contentType)) {
            throw new UnexpectedResponseException("Expected application/json response from server but got " + contentType, request, response);
        }
        try {
            String json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
            T value = jsonCodec.fromJson(json);
            return value;
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server");
        }
    }

}
