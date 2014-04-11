package com.proofpoint.stats;

import com.proofpoint.reporting.BucketIdProvider;
import com.proofpoint.reporting.Bucketed;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

public class TestSparseDistributionStat
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
        SparseDistributionStat distribution = new SparseDistributionStat();
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        SparseDistributionStat distribution = new SparseDistributionStat();
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2);
        distribution.add(3);
        assertPreviousDistribution(distribution, 1, 1, 1, 1);
        ++bucketIdProvider.id;
        distribution.add(1);
        distribution.add(-1);
        assertPreviousDistribution(distribution, 2, 2, 3, 5);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 2, -1, 1, 0);
    }

    @Test
    public void testDiscardsBuckets()
            throws Exception
    {
        SparseDistributionStat distribution = new SparseDistributionStat();
        distribution.add(100);
        distribution.setBucketIdProvider(bucketIdProvider);
        distribution.add(1);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        distribution.add(2);
        distribution.add(3);
        assertPreviousDistribution(distribution, 1, 1, 1, 1);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        distribution.add(100);
        assertPreviousDistributionEmpty(distribution);
        ++bucketIdProvider.id;
        assertPreviousDistribution(distribution, 1, 100, 100, 100);
        distribution.add(200);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousDistributionEmpty(distribution);
    }

    private void assertPreviousDistributionEmpty(SparseDistributionStat distribution)
            throws Exception
    {
        // Don't care about the precise values as long as none of them are reported
        assertPreviousDistribution(distribution, Double.NaN, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
    }

    private void assertPreviousDistribution(SparseDistributionStat distribution, double expectedCount, long expectedMin, long expectedMax, long expectedTotal)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        SparseDistributionStat.Distribution previousBucket = (SparseDistributionStat.Distribution) method.invoke(distribution);
        assertEquals(previousBucket.getCount(), expectedCount);
        assertEquals(previousBucket.getMin(), expectedMin);
        assertEquals(previousBucket.getMax(), expectedMax);
        assertEquals(previousBucket.getTotal(), expectedTotal);
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
