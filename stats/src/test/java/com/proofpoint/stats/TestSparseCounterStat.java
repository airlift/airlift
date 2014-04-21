package com.proofpoint.stats;

import com.proofpoint.reporting.Bucketed;
import com.proofpoint.stats.SparseCounterStat.Counter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestSparseCounterStat
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
        SparseCounterStat counter = new SparseCounterStat();
        counter.add(1);
        assertPreviousCountNull(counter);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        SparseCounterStat counter = new SparseCounterStat();
        counter.setBucketIdProvider(bucketIdProvider);
        counter.add(1);
        assertPreviousCountNull(counter);
        ++bucketIdProvider.id;
        counter.add(2);
        counter.add(2.2);
        assertPreviousCount(counter, 1);
        ++bucketIdProvider.id;
        assertPreviousCount(counter, 4.2);
    }

    @Test
    public void testDiscardsBuckets()
            throws Exception
    {
        SparseCounterStat counter = new SparseCounterStat();
        counter.add(100);
        counter.setBucketIdProvider(bucketIdProvider);
        counter.add(1);
        assertPreviousCountNull(counter);
        ++bucketIdProvider.id;
        counter.add(2);
        counter.add(2.2);
        assertPreviousCount(counter, 1);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        counter.add(100);
        assertPreviousCountNull(counter);
        ++bucketIdProvider.id;
        assertPreviousCount(counter, 100);
        counter.add(200);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousCountNull(counter);
    }

    private void assertPreviousCount(SparseCounterStat counter, double expected)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        Counter previousBucket = (Counter) method.invoke(counter);
        assertEquals(previousBucket.getCount(), expected);
    }
    
    private void assertPreviousCountNull(SparseCounterStat counter)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        Counter previousBucket = (Counter) method.invoke(counter);
        assertNull(previousBucket.getCount());
    }

}
