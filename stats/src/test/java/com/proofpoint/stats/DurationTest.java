package com.proofpoint.stats;

import org.testng.Assert;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import com.proofpoint.testing.EquivalenceTester;

public class DurationTest
{
    @Test
    public void testConvertTo()
    {
        double millis = 12346789.0d;
        Duration duration = new Duration(millis, MILLISECONDS);
        Assert.assertEquals(duration.toMillis(), millis);
        Assert.assertEquals(duration.convertTo(MILLISECONDS), millis);
        Assert.assertEquals(duration.convertTo(SECONDS), millis / 1000);
        Assert.assertEquals(duration.convertTo(MINUTES), millis / 1000 / 60);
        Assert.assertEquals(duration.convertTo(HOURS), millis / 1000 / 60 / 60);
        Assert.assertEquals(duration.convertTo(DAYS), millis / 1000 / 60 / 60 / 24);

        double days = 3.0;
        duration = new Duration(days, DAYS);
        Assert.assertEquals(duration.convertTo(DAYS), days);
        Assert.assertEquals(duration.convertTo(HOURS), days * 24);
        Assert.assertEquals(duration.convertTo(MINUTES), days * 24 * 60);
        Assert.assertEquals(duration.convertTo(SECONDS), days * 24 * 60 * 60);
        Assert.assertEquals(duration.convertTo(MILLISECONDS), days * 24 * 60 * 60 * 1000);
        Assert.assertEquals(duration.toMillis(), days * 24 * 60 * 60 * 1000);
    }

    @Test
    public void testToString()
    {
        Assert.assertEquals(new Duration(2.125, MILLISECONDS).toString(), "2.13ms");
        Assert.assertEquals(new Duration(2.125, MILLISECONDS).toString(MILLISECONDS), "2.13ms");
        Assert.assertEquals(new Duration(2.125, SECONDS).toString(SECONDS), "2.13s");
        Assert.assertEquals(new Duration(2.125, MINUTES).toString(MINUTES), "2.13m");
        Assert.assertEquals(new Duration(2.125, HOURS).toString(HOURS), "2.13h");
        Assert.assertEquals(new Duration(2.125, DAYS).toString(DAYS), "2.13d");
    }

    @Test
    public void testValueOf()
    {
        Assert.assertEquals(Duration.valueOf("1 ms"), new Duration(1, MILLISECONDS));
        Assert.assertEquals(Duration.valueOf("1 s"), new Duration(1, SECONDS));
        Assert.assertEquals(Duration.valueOf("1 m"), new Duration(1, MINUTES));
        Assert.assertEquals(Duration.valueOf("1 h"), new Duration(1, HOURS));
        Assert.assertEquals(Duration.valueOf("1 d"), new Duration(1, DAYS));

        Assert.assertEquals(Duration.valueOf("1.234 ms"), new Duration(1.234, MILLISECONDS));
        Assert.assertEquals(Duration.valueOf("1.234 s"), new Duration(1.234, SECONDS));
        Assert.assertEquals(Duration.valueOf("1.234 m"), new Duration(1.234, MINUTES));
        Assert.assertEquals(Duration.valueOf("1.234 h"), new Duration(1.234, HOURS));
        Assert.assertEquals(Duration.valueOf("1.234 d"), new Duration(1.234, DAYS));

        try {
            Duration.valueOf(null);
            fail("Expected NullPointerException");
        }
        catch (NullPointerException e) {
            // ok
        }

        try {
            Duration.valueOf("");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }

        try {
            Duration.valueOf("1.234 foo");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }

        try {
            Duration.valueOf("1.x34 ms");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }

        try {
            Duration.valueOf("1. ms");
            fail("Expected IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            // ok
        }

    }

    @Test
    public void testEquals()
    {
        Assert.assertEquals(new Duration(12359.0d, MILLISECONDS), new Duration(12359.0d, MILLISECONDS));
        Assert.assertFalse(new Duration(12359.0d, MILLISECONDS).equals(new Duration(4444.0d, MILLISECONDS)));
    }
    
    @Test
    public void testHashCode()
    {
        Assert.assertEquals(new Duration(12359.0d, MILLISECONDS).hashCode(), new Duration(12359.0d, MILLISECONDS).hashCode());
        Assert.assertFalse(new Duration(12359.0d, MILLISECONDS).hashCode() == new Duration(4444.0d, MILLISECONDS).hashCode());
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.checkComparison(generateTimeBucket(1), generateTimeBucket(2), generateTimeBucket(3));
    }

    private ArrayList<Duration> generateTimeBucket(double seconds)
    {
        ArrayList<Duration> bucket = new ArrayList<Duration>();
        bucket.add(new Duration(seconds * 1000 * 1000 * 1000, NANOSECONDS));
        bucket.add(new Duration(seconds * 1000 * 1000, MICROSECONDS));
        bucket.add(new Duration(seconds * 1000, MILLISECONDS));
        bucket.add(new Duration(seconds, SECONDS));
        bucket.add(new Duration(seconds / 60, MINUTES));
        bucket.add(new Duration(seconds / 60 / 60, HOURS));
        bucket.add(new Duration(seconds / 60 / 60 / 24, DAYS));
        return bucket;
    }

    @Test
    public void testNanoConversions()
    {
        double nanos = 1.0d;
        Duration duration = new Duration(nanos, NANOSECONDS);
        Assert.assertEquals(duration.toMillis(), nanos / 1000000);
        Assert.assertEquals(duration.convertTo(NANOSECONDS), nanos);
        Assert.assertEquals(duration.convertTo(MILLISECONDS), nanos / 1000000 );
        Assert.assertEquals(duration.convertTo(SECONDS), nanos / 1000000 / 1000);
        Assert.assertEquals(duration.convertTo(MINUTES), nanos / 1000000 / 1000 / 60, 1.0E10);
        Assert.assertEquals(duration.convertTo(HOURS), nanos / 1000000 / 1000 / 60 / 60, 1.0E10);
        Assert.assertEquals(duration.convertTo(DAYS), nanos / 1000000 / 1000 / 60 / 60 / 24, 1.0E10);

    }

    @Test
    public void invalidParameters()
    {
        failDurationConstruction(Double.NEGATIVE_INFINITY, MILLISECONDS);
        failDurationConstruction(Double.POSITIVE_INFINITY, MILLISECONDS);
        failDurationConstruction(Double.NaN, MILLISECONDS);
        failDurationConstruction(42, null);
        failDurationConstruction(-42, MILLISECONDS);

        Duration duration = new Duration(42, MILLISECONDS);
        try {
            duration.convertTo(null);
            fail("Expected NullPointerException");
        }
        catch (NullPointerException e) {
            // ok
        }
        try {
            duration.toString(null);
            fail("Expected NullPointerException");
        }
        catch (NullPointerException e) {
            // ok
        }
    }

    private void failDurationConstruction(double value, TimeUnit timeUnit) {
        try {
            new Duration(value, timeUnit);
            Assert.fail("Expected NullPointerException or IllegalArgumentException");
        }
        catch (NullPointerException e) {
            // ok
        }
        catch (IllegalArgumentException e) {
            // ok
        }
    }
}
