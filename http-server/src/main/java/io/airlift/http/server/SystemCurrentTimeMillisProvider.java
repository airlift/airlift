package io.airlift.http.server;

public class SystemCurrentTimeMillisProvider implements CurrentTimeMillisProvider
{
    @Override
    public long getCurrentTimeMillis()
    {
        return System.currentTimeMillis();
    }
}
