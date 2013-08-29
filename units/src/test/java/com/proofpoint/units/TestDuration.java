/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.units;

import com.proofpoint.json.JsonCodec;
import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDuration
{
    @Test
    public void testGetValue()
    {
        double millis = 12346789.0d;
        Duration duration = new Duration(millis, MILLISECONDS);
        Assert.assertEquals(duration.getValue(MILLISECONDS), millis);
        Assert.assertEquals(duration.getValue(SECONDS), millis / 1000, 0.001);
        Assert.assertEquals(duration.getValue(MINUTES), millis / 1000 / 60, 0.001);
        Assert.assertEquals(duration.getValue(HOURS), millis / 1000 / 60 / 60, 0.001);
        Assert.assertEquals(duration.getValue(DAYS), millis / 1000 / 60 / 60 / 24, 0.001);

        double days = 3.0;
        duration = new Duration(days, DAYS);
        Assert.assertEquals(duration.getValue(DAYS), days);
        Assert.assertEquals(duration.getValue(HOURS), days * 24, 0.001);
        Assert.assertEquals(duration.getValue(MINUTES), days * 24 * 60, 0.001);
        Assert.assertEquals(duration.getValue(SECONDS), days * 24 * 60 * 60, 0.001);
        Assert.assertEquals(duration.getValue(MILLISECONDS), days * 24 * 60 * 60 * 1000, 0.001);
    }

//    @Test(dataProvider = "conversions")
//    public void testConversions(TimeUnit unit, TimeUnit toTimeUnit, double factor)
//    {
//        Duration duration = new Duration(1, unit).convertTo(toTimeUnit);
//        assertEquals(duration.getUnit(), toTimeUnit);
//        assertEquals(duration.getValue(), factor, factor * 0.001);
//        assertEquals(duration.getValue(toTimeUnit), factor, factor * 0.001);
//    }

    @Test(dataProvider = "conversions")
    public void testConvertToMostSuccinctDuration(TimeUnit unit, TimeUnit toTimeUnit, double factor)
    {
        Duration duration = new Duration(factor, toTimeUnit);
        Duration actual = duration.convertToMostSuccinctTimeUnit();
        assertEquals(actual.getValue(toTimeUnit), factor, factor * 0.001);
        assertEquals(actual.getValue(unit), 1.0,  0.001);
        if (actual.getUnit() != unit) {
            assertEquals(actual.getUnit(), unit);
        }
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.<Duration>comparisonTester()
                .addLesserGroup(generateTimeBucket(0))
                .addGreaterGroup(generateTimeBucket(1))
                .addGreaterGroup(generateTimeBucket(123352))
                .addGreaterGroup(generateTimeBucket(Long.MAX_VALUE))
                .check();
    }

    private ArrayList<Duration> generateTimeBucket(double seconds)
    {
        ArrayList<Duration> bucket = new ArrayList<>();
        bucket.add(new Duration(seconds * 1000 * 1000 * 1000, NANOSECONDS));
        bucket.add(new Duration(seconds * 1000 * 1000, MICROSECONDS));
        bucket.add(new Duration(seconds * 1000, MILLISECONDS));
        bucket.add(new Duration(seconds, SECONDS));
        bucket.add(new Duration(seconds / 60, MINUTES));
        bucket.add(new Duration(seconds / 60 / 60, HOURS));
        // skip days for larger values as this results in rounding errors
        if (seconds <= 1.0) {
            bucket.add(new Duration(seconds / 60 / 60/ 24, DAYS));
        }
        return bucket;
    }

    @Test(dataProvider = "printedValues")
    public void testToString(String expectedString, double value, TimeUnit unit)
    {
        assertEquals(new Duration(value, unit).toString(), expectedString);
    }

    @Test(dataProvider = "parseableValues")
    public void testValueOf(String string, double expectedValue, TimeUnit expectedUnit)
    {
        Duration duration = Duration.valueOf(string);

        assertEquals(duration.getUnit(), expectedUnit);
        assertEquals(duration.getValue(), expectedValue);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "duration is null")
    public void testValueOfRejectsNull()
    {
        Duration.valueOf(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "duration is empty")
    public void testValueOfRejectsEmptyString()
    {
        Duration.valueOf("");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Unknown time unit: kg")
    public void testValueOfRejectsInvalidUnit()
    {
        Duration.valueOf("1.234 kg");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "duration is not a valid.*")
    public void testValueOfRejectsInvalidNumber()
    {
        Duration.valueOf("1.2x4 s");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "value is negative")
    public void testConstructorRejectsNegativeValue()
    {
        new Duration(-1, SECONDS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "value is infinite")
    public void testConstructorRejectsInfiniteValue()
    {
        new Duration(Double.POSITIVE_INFINITY, SECONDS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "value is infinite")
    public void testConstructorRejectsInfiniteValue2()
    {
        new Duration(Double.NEGATIVE_INFINITY, SECONDS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "value is not a number")
    public void testConstructorRejectsNaN()
    {
        new Duration(Double.NaN, SECONDS);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "unit is null")
    public void testConstructorRejectsNullUnit()
    {
        new Duration(1, null);
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
    public void testNanoConversions()
    {
        double nanos = 1.0d;
        Duration duration = new Duration(nanos, NANOSECONDS);
        Assert.assertEquals(duration.getValue(), nanos);
        Assert.assertEquals(duration.getValue(NANOSECONDS), nanos);
        Assert.assertEquals(duration.getValue(MILLISECONDS), nanos / 1000000);
        Assert.assertEquals(duration.getValue(SECONDS), nanos / 1000000 / 1000);
        Assert.assertEquals(duration.getValue(MINUTES), nanos / 1000000 / 1000 / 60, 1.0E10);
        Assert.assertEquals(duration.getValue(HOURS), nanos / 1000000 / 1000 / 60 / 60, 1.0E10);
        Assert.assertEquals(duration.getValue(DAYS), nanos / 1000000 / 1000 / 60 / 60 / 24, 1.0E10);

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

    @Test
    public void testJsonRoundTrip()
            throws Exception
    {
        assertJsonRoundTrip(new Duration(1.234, MILLISECONDS));
        assertJsonRoundTrip(new Duration(1.234, SECONDS));
        assertJsonRoundTrip(new Duration(1.234, MINUTES));
        assertJsonRoundTrip(new Duration(1.234, HOURS));
        assertJsonRoundTrip(new Duration(1.234, DAYS));

    }

    private void assertJsonRoundTrip(Duration duration)
            throws IOException
    {
        JsonCodec<Duration> durationCodec = JsonCodec.jsonCodec(Duration.class);
        String json = durationCodec.toJson(duration);
        Duration durationCopy = durationCodec.fromJson(json);
        double delta = duration.getValue(MILLISECONDS) * 0.01;
        Assert.assertEquals(duration.getValue(MILLISECONDS), durationCopy.getValue(MILLISECONDS), delta);
    }

    private void failDurationConstruction(double value, TimeUnit timeUnit)
    {
        try {
            new Duration(value, timeUnit);
            Assert.fail("Expected NullPointerException or IllegalArgumentException");
        }
        catch (NullPointerException | IllegalArgumentException e) {
            // ok
        }
    }


    @DataProvider(name = "parseableValues", parallel = true)
    private Object[][] parseableValues()
    {
        return new Object[][]{
                // spaces
                new Object[]{"1234 ns", 1234, NANOSECONDS},
                new Object[]{"1234 ms", 1234, MILLISECONDS},
                new Object[]{"1234 s", 1234, SECONDS},
                new Object[]{"1234 m", 1234, MINUTES},
                new Object[]{"1234 h", 1234, HOURS},
                new Object[]{"1234 d", 1234, DAYS},
                new Object[]{"1234.567 ns", 1234.567, NANOSECONDS},
                new Object[]{"1234.567 ms", 1234.567, MILLISECONDS},
                new Object[]{"1234.567 s", 1234.567, SECONDS},
                new Object[]{"1234.567 m", 1234.567, MINUTES},
                new Object[]{"1234.567 h", 1234.567, HOURS},
                new Object[]{"1234.567 d", 1234.567, DAYS},
                // no spaces
                new Object[]{"1234ns", 1234, NANOSECONDS},
                new Object[]{"1234ms", 1234, MILLISECONDS},
                new Object[]{"1234s", 1234, SECONDS},
                new Object[]{"1234m", 1234, MINUTES},
                new Object[]{"1234h", 1234, HOURS},
                new Object[]{"1234d", 1234, DAYS},
                new Object[]{"1234.567ns", 1234.567, NANOSECONDS},
                new Object[]{"1234.567ms", 1234.567, MILLISECONDS},
                new Object[]{"1234.567s", 1234.567, SECONDS},
                new Object[]{"1234.567m", 1234.567, MINUTES},
                new Object[]{"1234.567h", 1234.567, HOURS},
                new Object[]{"1234.567d", 1234.567, DAYS}
        };
    }

    @DataProvider(name = "printedValues", parallel = true)
    private Object[][] printedValues()
    {
        return new Object[][]{
                new Object[]{"1234.00ns", 1234, NANOSECONDS},
                new Object[]{"1234.00us", 1234, MICROSECONDS},
                new Object[]{"1234.00ms", 1234, MILLISECONDS},
                new Object[]{"1234.00s", 1234, SECONDS},
                new Object[]{"1234.00m", 1234, MINUTES},
                new Object[]{"1234.00h", 1234, HOURS},
                new Object[]{"1234.00d", 1234, DAYS},
                new Object[]{"1234.57ns", 1234.567, NANOSECONDS},
                new Object[]{"1234.57us", 1234.567, MICROSECONDS},
                new Object[]{"1234.57ms", 1234.567, MILLISECONDS},
                new Object[]{"1234.57s", 1234.567, SECONDS},
                new Object[]{"1234.57m", 1234.567, MINUTES},
                new Object[]{"1234.57h", 1234.567, HOURS},
                new Object[]{"1234.57d", 1234.567, DAYS}
        };
    }

    @DataProvider(name = "conversions", parallel = true)
    private Object[][] conversions()
    {
        return new Object[][]{
                new Object[]{NANOSECONDS, NANOSECONDS, 1.0},
                new Object[]{NANOSECONDS, MILLISECONDS, 1.0 / 1000_000},
                new Object[]{NANOSECONDS, SECONDS, 1.0 / 1000_000 / 1000},
                new Object[]{NANOSECONDS, MINUTES, 1.0 / 1000_000 / 1000 / 60},
                new Object[]{NANOSECONDS, HOURS, 1.0 / 1000_000 / 1000 / 60 / 60},
                new Object[]{NANOSECONDS, DAYS, 1.0 / 1000_000 / 1000 / 60 / 60 / 24},

                new Object[]{MILLISECONDS, NANOSECONDS, 1000000.0},
                new Object[]{MILLISECONDS, MILLISECONDS, 1.0},
                new Object[]{MILLISECONDS, SECONDS, 1.0 / 1000},
                new Object[]{MILLISECONDS, MINUTES, 1.0 / 1000 / 60},
                new Object[]{MILLISECONDS, HOURS, 1.0 / 1000 / 60 / 60},
                new Object[]{MILLISECONDS, DAYS, 1.0 / 1000 / 60 / 60 / 24},

                new Object[]{SECONDS, NANOSECONDS, 1000000.0 * 1000},
                new Object[]{SECONDS, MILLISECONDS, 1000},
                new Object[]{SECONDS, SECONDS, 1},
                new Object[]{SECONDS, MINUTES, 1.0 / 60},
                new Object[]{SECONDS, HOURS, 1.0 / 60 / 60},
                new Object[]{SECONDS, DAYS, 1.0 / 60 / 60 / 24},

                new Object[]{MINUTES, NANOSECONDS, 1000000.0 * 1000 * 60},
                new Object[]{MINUTES, MILLISECONDS, 1000 * 60},
                new Object[]{MINUTES, SECONDS, 60},
                new Object[]{MINUTES, MINUTES, 1},
                new Object[]{MINUTES, HOURS, 1.0 / 60},
                new Object[]{MINUTES, DAYS, 1.0 / 60 / 24},

                new Object[]{HOURS, NANOSECONDS, 1000000.0 * 1000 * 60 * 60},
                new Object[]{HOURS, MILLISECONDS, 1000 * 60 * 60},
                new Object[]{HOURS, SECONDS, 60 * 60},
                new Object[]{HOURS, MINUTES, 60},
                new Object[]{HOURS, HOURS, 1},
                new Object[]{HOURS, DAYS, 1.0 / 24},

                new Object[]{DAYS, NANOSECONDS, 1000000.0 * 1000 * 60 * 60 * 24},
                new Object[]{DAYS, MILLISECONDS, 1000 * 60 * 60 * 24},
                new Object[]{DAYS, SECONDS, 60 * 60 * 24},
                new Object[]{DAYS, MINUTES, 60 * 24},
                new Object[]{DAYS, HOURS, 24},
                new Object[]{DAYS, DAYS, 1},
        };
    }

}
