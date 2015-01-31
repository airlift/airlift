package com.proofpoint.http.client;

public class ResponseStatusCodeHandler
        implements ResponseHandler<Integer, Exception>
{
    @Override
    public Integer handleException(Request request, Exception exception)
            throws Exception
    {
        throw exception;
    }

    @Override
    public Integer handle(Request request, Response response)
            throws Exception
    {
        return response.getStatusCode();
    }
}
