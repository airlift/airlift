package com.proofpoint.http.client;

import static com.proofpoint.http.client.Request.Builder.fromRequest;

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
