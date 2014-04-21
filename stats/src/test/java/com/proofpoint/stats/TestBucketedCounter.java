package com.proofpoint.stats;

import com.proofpoint.reporting.Bucketed;
import com.proofpoint.stats.BucketedCounter.Counter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

public class TestBucketedCounter
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
        BucketedCounter counter = new BucketedCounter();
        counter.add(1);
        assertPreviousCount(counter, 0);
    }

    @Test
    public void testWithBucketIdProvider()
            throws Exception
    {
        BucketedCounter counter = new BucketedCounter();
        counter.setBucketIdProvider(bucketIdProvider);
        counter.add(1);
        assertPreviousCount(counter, 0);
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
        BucketedCounter counter = new BucketedCounter();
        counter.add(100);
        counter.setBucketIdProvider(bucketIdProvider);
        counter.add(1);
        assertPreviousCount(counter, 0);
        ++bucketIdProvider.id;
        counter.add(2);
        counter.add(2.2);
        assertPreviousCount(counter, 1);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        counter.add(100);
        assertPreviousCount(counter, 0);
        ++bucketIdProvider.id;
        assertPreviousCount(counter, 100);
        counter.add(200);
        ++bucketIdProvider.id;
        ++bucketIdProvider.id;
        assertPreviousCount(counter, 0);
    }

    private void assertPreviousCount(BucketedCounter counter, double expected)
            throws Exception
    {
        Method method = Bucketed.class.getDeclaredMethod("getPreviousBucket");
        method.setAccessible(true);
        Counter previousBucket = (Counter) method.invoke(counter);
        assertEquals(previousBucket.getCount(), expected);
    }
}
