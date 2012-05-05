package com.proofpoint.http.client;

public class TestingRequestFilter
        implements HttpRequestFilter
{
    @Override
    public Request filterRequest(Request request)
    {
        return RequestBuilder.fromRequest(request)
                .addHeader("x-custom-filter", "customvalue")
                .build();
    }
}
