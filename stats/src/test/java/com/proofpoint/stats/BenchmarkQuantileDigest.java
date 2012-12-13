package com.proofpoint.stats;

import com.proofpoint.units.Duration;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class BenchmarkQuantileDigest
{
    public static void main(String[] args)
            throws Exception
    {
        Duration warmupTime = new Duration(3, TimeUnit.SECONDS);
        Duration benchmarkTime = new Duration(5, TimeUnit.SECONDS);

        final QuantileDigest digest = new QuantileDigest(0.01, 0, new TestingTicker(), true);
        final Random random = new Random();

        Benchmark.Results results = Benchmark.run(new Runnable() {
            public void run()
            {
                digest.add(Math.abs(random.nextInt(100000)));
            }
        }, warmupTime, benchmarkTime);

        digest.validate();

        System.out.println(String.format("Processed %s entries in %s ms. Insertion rate = %s entries/s (%.4fµs per operation)",
                results.getOperations(),
                results.getTime().getValue(TimeUnit.MILLISECONDS),
                results.getOperationsPerSecond(),
                results.getTimePerOperation().getValue(TimeUnit.MICROSECONDS)));

        System.out.println(String.format("Compressions: %s, %s entries/compression",
                digest.getCompressions(),
                digest.getCount() / digest.getCompressions()));
    }

}
