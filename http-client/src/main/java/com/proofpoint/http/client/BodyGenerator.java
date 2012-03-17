package com.proofpoint.http.client;

import com.google.common.annotations.Beta;

import java.io.OutputStream;

@Beta
public interface BodyGenerator
{
    public void write(OutputStream out)
            throws Exception;
}
