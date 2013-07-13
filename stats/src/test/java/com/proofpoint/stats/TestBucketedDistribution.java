package com.proofpoint.stats;

import com.proofpoint.reporting.BucketIdProvider;
import com.proofpoint.reporting.Bucketed;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

public class TestBucketedDistribution
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
        BucketedDistribution distribution = new BucketedDistribution();
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        BucketedDistribution distribution = new BucketedDistribution();
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2);
        distribution.add(3);
        assertPreviousDistribution(distribution, 1, 1, 1);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 2, 2, 3);
    }

    @Test
    public void testDiscardsBuckets()
            throws Exception
    {
        BucketedDistribution distribution = new BucketedDistribution();
        distribution.add(100);
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2);
        distribution.add(3);
        assertPreviousDistribution(distribution, 1, 1, 1);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        distribution.add(100);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 1, 100, 100);
        distribution.add(200);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousDistributionEmpty(distribution);
    }

    private void assertPreviousDistributionEmpty(BucketedDistribution distribution)
            throws Exception
    {
        assertPreviousDistribution(distribution, 0, Long.MAX_VALUE, Long.MIN_VALUE);
    }

    private void assertPreviousDistribution(BucketedDistribution distribution, int expectedCount, long expectedMin, long expectedMax)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        BucketedDistribution.Distribution previousBucket = (BucketedDistribution.Distribution) method.invoke(distribution);
        assertEquals(previousBucket.getCount(), (double) expectedCount);
        assertEquals(previousBucket.getMin(), expectedMin);
        assertEquals(previousBucket.getMax(), expectedMax);
    }

    private static class TestingBucketIdProvider
        implements BucketIdProvider
    {
        private int id = 0;

        @Override
        public int get()
        {
            return id;
        }
    }
}
