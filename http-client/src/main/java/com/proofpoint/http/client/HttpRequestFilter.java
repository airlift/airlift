package com.proofpoint.http.client;

public interface HttpRequestFilter
{
    Request filterRequest(Request request);
}
