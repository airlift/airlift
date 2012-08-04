package com.proofpoint.stats;

class RealtimeWallClock
        implements WallClock
{
    @Override
    public long getMillis() {
        return System.currentTimeMillis();
    }
}
