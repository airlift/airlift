package com.proofpoint.stats;

import com.proofpoint.reporting.Bucketed;
import com.proofpoint.stats.SparseTimeStat.BlockTimer;
import com.proofpoint.testing.TestingTicker;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;

public class TestSparseTimeStat
{
    private TestingBucketIdProvider bucketIdProvider;
    private TestingTicker ticker;

    @BeforeMethod
    public void setup()
    {
        bucketIdProvider = new TestingBucketIdProvider();
        ticker = new TestingTicker();
    }

    @Test
    public void testInitialState()
            throws Exception
    {
        SparseTimeStat stat = new SparseTimeStat();
        stat.add(1, NANOSECONDS);
        assertPreviousDistributionEmpty(stat);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        SparseTimeStat stat = new SparseTimeStat();
        stat.setBucketIdProvider(bucketIdProvider);
        stat.add(1, MILLISECONDS);
        assertPreviousDistributionEmpty(stat);
        ++bucketIdProvider.id;
        stat.add(new Duration(2, MILLISECONDS));
        stat.add(3_000_000, NANOSECONDS);
        assertPreviousDistribution(stat, 1, .001, .001);
        ++bucketIdProvider.id;
        assertPreviousDistribution(stat, 2, .002, .003);
    }

    @Test
    public void testDiscardsBuckets()
            throws Exception
    {
        SparseTimeStat distribution = new SparseTimeStat();
        distribution.add(100, MILLISECONDS);
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1, MILLISECONDS);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2, MILLISECONDS);
        distribution.add(3, MILLISECONDS);
        assertPreviousDistribution(distribution, 1, .001, .001);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        distribution.add(0.1, SECONDS);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 1, .1, .1);
        distribution.add(0.2, SECONDS);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousDistributionEmpty(distribution);
    }

    @Test
    public void testTimeCallable()
            throws Exception
    {
        SparseTimeStat stat = new SparseTimeStat(ticker);
        stat.setBucketIdProvider(bucketIdProvider);

        stat.time(new Callable<Void>()
        {
            @Override
            public Void call()
            {
                ticker.increment(10, TimeUnit.MILLISECONDS);
                return null;
            }
        });

        ++bucketIdProvider.id;
        assertPreviousDistribution(stat, 1, 0.010, 0.010);
    }

    @Test
    public void testTimeTry()
            throws Exception
    {
        SparseTimeStat stat = new SparseTimeStat(ticker);
        stat.setBucketIdProvider(bucketIdProvider);

        try (BlockTimer ignored = stat.time()) {
            ticker.increment(10, TimeUnit.MILLISECONDS);
        }

        ++bucketIdProvider.id;
        assertPreviousDistribution(stat, 1, 0.010, 0.010);
    }

    private void assertPreviousDistributionEmpty(SparseTimeStat distribution)
            throws Exception
    {
        assertPreviousDistribution(distribution, Double.NaN, Double.NaN, Double.NaN);
    }

    private void assertPreviousDistribution(SparseTimeStat distribution, double expectedCount, double expectedMin, double expectedMax)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        SparseTimeStat.Distribution previousBucket = (SparseTimeStat.Distribution) method.invoke(distribution);
        assertEquals(previousBucket.getCount(), expectedCount);
        assertEquals(previousBucket.getMin(), expectedMin);
        assertEquals(previousBucket.getMax(), expectedMax);
    }
}
