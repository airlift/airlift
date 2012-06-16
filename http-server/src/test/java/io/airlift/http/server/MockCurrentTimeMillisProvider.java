package io.airlift.http.server;

public class MockCurrentTimeMillisProvider implements CurrentTimeMillisProvider
{
    private long time;

    public MockCurrentTimeMillisProvider(long time)
    {
        this.time = time;
    }

    public void incrementTime(long delta) {
        time += delta;
    }

    @Override
    public long getCurrentTimeMillis()
    {
        return time;
    }
}
