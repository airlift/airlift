package com.proofpoint.http.client.netty;

public class CanceledRequestException extends Exception {
    public CanceledRequestException()
    {
        super("Request was canceled");
    }
}
