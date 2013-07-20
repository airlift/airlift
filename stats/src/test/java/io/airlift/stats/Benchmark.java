package io.airlift.stats;

import io.airlift.units.Duration;

import java.util.concurrent.TimeUnit;

public class Benchmark
{
    public static Results run(Runnable task, Duration warmupTime, Duration benchmarkTime)
            throws Exception
    {
        double warmupNanos = warmupTime.getValue(TimeUnit.NANOSECONDS);
        double totalNanos = warmupNanos + benchmarkTime.getValue(TimeUnit.NANOSECONDS);

        long operations = 0;
        long begin = System.nanoTime();
        long benchmarkStart = begin;
        long elapsed;
        do {
            task.run();

            long now = System.nanoTime();
            elapsed = now - begin;

            if (elapsed < warmupNanos) {
                // warming up
                benchmarkStart = now;
            }
            else {
                ++operations;
            }
        }
        while (elapsed < totalNanos);

        long benchmarkElapsed = System.nanoTime() - benchmarkStart;

        return new Results(new Duration(benchmarkElapsed, TimeUnit.NANOSECONDS), operations);
    }

    public static class Results
    {
        private final Duration time;
        private final long operations;

        public Results(Duration time, long operations)
        {
            this.time = time;
            this.operations = operations;
        }

        public Duration getTime()
        {
            return time;
        }

        public long getOperations()
        {
            return operations;
        }

        public double getOperationsPerSecond()
        {
            return operations / time.getValue(TimeUnit.SECONDS);
        }

        public Duration getTimePerOperation()
        {
            return new Duration(time.getValue(TimeUnit.MILLISECONDS) / operations, TimeUnit.MILLISECONDS);
        }
    }
}
