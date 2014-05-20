package com.proofpoint.stats;

import com.proofpoint.reporting.Bucketed;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

public class TestBucketedTimeDistribution
{
    private TestingBucketIdProvider bucketIdProvider;

    @BeforeMethod
    public void setup()
    {
        bucketIdProvider = new TestingBucketIdProvider();
    }

    @Test
    public void testInitialState()
            throws Exception
    {
        BucketedTimeDistribution distribution = new BucketedTimeDistribution();
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        BucketedTimeDistribution distribution = new BucketedTimeDistribution();
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1_000_000);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2_000_000);
        distribution.add(3_000_000);
        assertPreviousDistribution(distribution, 1, .001, .001, .001);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 2, .002, .003, .005);
    }

    @Test
    public void testDiscardsBuckets()
            throws Exception
    {
        BucketedTimeDistribution distribution = new BucketedTimeDistribution();
        distribution.add(100_000_000);
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1_000_000);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2_000_000);
        distribution.add(3_000_000);
        assertPreviousDistribution(distribution, 1, .001, .001, .001);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        distribution.add(100_000_000);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 1, .1, .1, .1);
        distribution.add(200_000_000);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousDistributionEmpty(distribution);
    }

    private void assertPreviousDistributionEmpty(BucketedTimeDistribution distribution)
            throws Exception
    {
        assertPreviousDistribution(distribution, 0, Double.NaN, Double.NaN, 0.0);
    }

    private void assertPreviousDistribution(BucketedTimeDistribution distribution, int expectedCount, double expectedMin, double expectedMax, double expectedTotal)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        BucketedTimeDistribution.Distribution previousBucket = (BucketedTimeDistribution.Distribution) method.invoke(distribution);
        assertEquals(previousBucket.getCount(), (double) expectedCount);
        assertEquals(previousBucket.getMin(), expectedMin);
        assertEquals(previousBucket.getMax(), expectedMax);
        assertEquals(previousBucket.getTotal(), expectedTotal);
    }
}
