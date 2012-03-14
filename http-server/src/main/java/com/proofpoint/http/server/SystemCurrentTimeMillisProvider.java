package com.proofpoint.http.server;

public class SystemCurrentTimeMillisProvider implements CurrentTimeMillisProvider
{
    @Override
    public long getCurrentTimeMillis()
    {
        return System.currentTimeMillis();
    }
}
