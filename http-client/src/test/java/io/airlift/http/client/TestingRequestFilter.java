package io.airlift.http.client;

import static io.airlift.http.client.Request.Builder.fromRequest;

public class TestingRequestFilter
        implements HttpRequestFilter
{
    @Override
    public Request filterRequest(Request request)
    {
        return fromRequest(request)
                .addHeader("x-custom-filter", "customvalue")
                .build();
    }
}
