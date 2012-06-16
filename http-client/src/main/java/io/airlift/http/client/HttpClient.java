package com.proofpoint.http.client;

import com.google.common.annotations.Beta;

@Beta
public interface HttpClient
{
    RequestStats getStats();

    <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
            throws E;
}
