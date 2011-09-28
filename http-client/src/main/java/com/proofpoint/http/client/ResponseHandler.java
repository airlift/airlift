package com.proofpoint.http.client;

public interface ResponseHandler<T, E extends Exception>
{
    E handleException(Request request, Exception exception);

    T handle(Request request, Response response)
            throws E;
}
