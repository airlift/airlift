package io.airlift.stats;

class RealtimeWallClock
        implements WallClock
{
    @Override
    public long getMillis() {
        return System.currentTimeMillis();
    }
}
