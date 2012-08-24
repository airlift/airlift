package io.airlift.stats;

class RealtimeWallClock
        implements WallClock
{
    @Override
    public long getMillis() {
        return System.nanoTime() / 1_000_000;
    }
}
