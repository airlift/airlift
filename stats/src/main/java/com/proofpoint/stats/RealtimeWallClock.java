package com.proofpoint.stats;

class RealtimeWallClock
        implements WallClock
{
    @Override
    public long getMillis() {
        return System.nanoTime() / 1_000_000;
    }
}
