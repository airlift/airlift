package com.proofpoint.http.client;

import java.io.OutputStream;

public interface BodyGenerator
{
    public void write(OutputStream out)
            throws Exception;
}
