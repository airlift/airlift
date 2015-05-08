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
package io.airlift.units;

import com.google.common.collect.ImmutableList;
import io.airlift.json.JsonCodec;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Locale;

import static io.airlift.testing.EquivalenceTester.comparisonTester;
import static io.airlift.units.DataSize.Unit.BYTE;
import static io.airlift.units.DataSize.Unit.GIGABYTE;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static io.airlift.units.DataSize.Unit.PETABYTE;
import static io.airlift.units.DataSize.Unit.TERABYTE;
import static io.airlift.units.DataSize.succinctBytes;
import static io.airlift.units.DataSize.succinctDataSize;
import static org.testng.Assert.assertEquals;

public class TestDataSize
{
    @Test
    public void testSuccinctFactories()
    {
        assertEquals(succinctBytes(123), new DataSize(123, BYTE));
        assertEquals(succinctBytes((long) (5.5 * 1024)), new DataSize(5.5, KILOBYTE));
        assertEquals(succinctBytes(5 * 1024 * 1024), new DataSize(5, MEGABYTE));

        assertEquals(succinctDataSize(123, BYTE), new DataSize(123, BYTE));
        assertEquals(succinctDataSize((long) (5.5 * 1024), BYTE), new DataSize(5.5, KILOBYTE));
        assertEquals(succinctDataSize(5 * 1024, KILOBYTE), new DataSize(5, MEGABYTE));
    }

    @Test(dataProvider = "conversions")
    public void testConversions(DataSize.Unit unit, DataSize.Unit toUnit, double factor)
    {
        DataSize size = new DataSize(1, unit).convertTo(toUnit);
        assertEquals(size.getUnit(), toUnit);
        assertEquals(size.getValue(), factor);

        assertEquals(size.getValue(toUnit), factor);
    }

    @Test(dataProvider = "conversions")
    public void testConvertToMostSuccinctDataSize(DataSize.Unit unit, DataSize.Unit toUnit, double factor)
    {
        DataSize size = new DataSize(factor, toUnit);
        DataSize actual = size.convertToMostSuccinctDataSize();
        assertEquals(actual, new DataSize(1, unit));
        assertEquals(actual.getValue(unit), 1.0, 0.001);
        assertEquals(actual.getUnit(), unit);
    }

    @Test
    public void testEquivalence()
    {
        comparisonTester()
                .addLesserGroup(group(0))
                .addGreaterGroup(group(1))
                .addGreaterGroup(group(123352))
                .addGreaterGroup(group(Long.MAX_VALUE))
                .check();
    }

    private Iterable<DataSize> group(double bytes)
    {
        return ImmutableList.of(
                new DataSize(bytes, BYTE),
                new DataSize(bytes / 1024, KILOBYTE),
                new DataSize(bytes / 1024 / 1024, MEGABYTE),
                new DataSize(bytes / 1024 / 1024 / 1024, GIGABYTE),
                new DataSize(bytes / 1024 / 1024 / 1024 / 1024, TERABYTE),
                new DataSize(bytes / 1024 / 1024 / 1024 / 1024 / 1024, PETABYTE)
        );
    }

    @Test(dataProvider = "printedValues")
    public void testToString(String expectedString, double value, DataSize.Unit unit)
    {
        assertEquals(new DataSize(value, unit).toString(), expectedString);
    }

    @Test(dataProvider = "printedValues")
    public void testNonEnglishLocale(String expectedString, double value, DataSize.Unit unit)
    {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMAN);
        try {
            assertEquals(new DataSize(value, unit).toString(), expectedString);
        }
        finally {
            Locale.setDefault(previous);
        }
    }

    @Test(dataProvider = "parseableValues")
    public void testValueOf(String string, double expectedValue, DataSize.Unit expectedUnit)
    {
        DataSize size = DataSize.valueOf(string);

        assertEquals(size.getUnit(), expectedUnit);
        assertEquals(size.getValue(), expectedValue);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "size is null")
    public void testValueOfRejectsNull()
    {
        DataSize.valueOf(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is empty")
    public void testValueOfRejectsEmptyString()
    {
        DataSize.valueOf("");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Unknown unit: kg")
    public void testValueOfRejectsInvalidUnit()
    {
        DataSize.valueOf("1.234 kg");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is not a valid.*")
    public void testValueOfRejectsInvalidNumber()
    {
        DataSize.valueOf("1.2x4 B");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is negative")
    public void testConstructorRejectsNegativeSize()
    {
        new DataSize(-1, BYTE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is infinite")
    public void testConstructorRejectsInfiniteSize()
    {
        new DataSize(Double.POSITIVE_INFINITY, BYTE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is infinite")
    public void testConstructorRejectsInfiniteSize2()
    {
        new DataSize(Double.NEGATIVE_INFINITY, BYTE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is not a number")
    public void testConstructorRejectsNaN()
    {
        new DataSize(Double.NaN, BYTE);
    }

    @Test(expectedExceptions = NullPointerException.class, expectedExceptionsMessageRegExp = "unit is null")
    public void testConstructorRejectsNullUnit()
    {
        new DataSize(1, null);
    }

    @Test
    public void testToBytes()
    {
        assertEquals(new DataSize(0, BYTE).toBytes(), 0);
        assertEquals(new DataSize(0, MEGABYTE).toBytes(), 0);
        assertEquals(new DataSize(1, BYTE).toBytes(), 1);
        assertEquals(new DataSize(1, KILOBYTE).toBytes(), 1024);
        assertEquals(new DataSize(42, MEGABYTE).toBytes(), 42L * 1024 * 1024);
        assertEquals(new DataSize(0.037, KILOBYTE).toBytes(), 37);
        assertEquals(new DataSize(1, TERABYTE).toBytes(), 1024L * 1024 * 1024 * 1024);
        assertEquals(new DataSize(1, PETABYTE).toBytes(), 1024L * 1024 * 1024 * 1024 * 1024);
        assertEquals(new DataSize(1024, PETABYTE).toBytes(), 1024L * 1024 * 1024 * 1024 * 1024 * 1024);
        assertEquals(new DataSize(8191, PETABYTE).toBytes(), 8191L * 1024 * 1024 * 1024 * 1024 * 1024);
        assertEquals(new DataSize(Long.MAX_VALUE, BYTE).toBytes(), Long.MAX_VALUE);
        assertEquals(new DataSize(Long.MAX_VALUE / 1024.0, KILOBYTE).toBytes(), Long.MAX_VALUE);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "size is too large .*")
    public void testToBytesTooLarge()
    {
        new DataSize(Long.MAX_VALUE + 1024.0001, BYTE).toBytes(); // smallest value that overflows
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "size is too large .*")
    public void testToBytesTooLarge2()
    {
        new DataSize(9000, PETABYTE).toBytes();
    }

    @Test
    public void testRoundTo()
    {
        assertEquals(new DataSize(0, BYTE).roundTo(BYTE), 0);
        assertEquals(new DataSize(0.5, BYTE).roundTo(BYTE), 1);
        assertEquals(new DataSize((42 * 1024) + 511, BYTE).roundTo(KILOBYTE), 42);
        assertEquals(new DataSize((42 * 1024) + 512, BYTE).roundTo(KILOBYTE), 43);
        assertEquals(new DataSize(513, TERABYTE).roundTo(PETABYTE), 1);
        assertEquals(new DataSize(9000L * 1024 * 1024 * 1024 * 1024, KILOBYTE).roundTo(PETABYTE), 9000);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is too large .*")
    public void testRoundToTooLarge()
    {
        new DataSize(Long.MAX_VALUE + 1024.0001, BYTE).roundTo(BYTE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is too large .*")
    public void testRoundToTooLarge2()
    {
        new DataSize(9000, PETABYTE).roundTo(BYTE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "size is too large .*")
    public void testRoundToTooLarge3()
    {
        new DataSize(9000 * 1024, PETABYTE).roundTo(KILOBYTE);
    }

    @Test
    public void testJsonRoundTrip()
            throws Exception
    {
        assertJsonRoundTrip(new DataSize(1.234, BYTE));
        assertJsonRoundTrip(new DataSize(1.234, KILOBYTE));
        assertJsonRoundTrip(new DataSize(1.234, MEGABYTE));
        assertJsonRoundTrip(new DataSize(1.234, GIGABYTE));
        assertJsonRoundTrip(new DataSize(1.234, TERABYTE));
        assertJsonRoundTrip(new DataSize(1.234, PETABYTE));

    }

    private void assertJsonRoundTrip(DataSize dataSize)
            throws IOException
    {
        JsonCodec<DataSize> dataSizeCodec = JsonCodec.jsonCodec(DataSize.class);
        String json = dataSizeCodec.toJson(dataSize);
        DataSize dataSizeCopy = dataSizeCodec.fromJson(json);
        double delta = dataSize.toBytes() * 0.01;
        Assert.assertEquals(dataSize.toBytes(), dataSizeCopy.toBytes(), delta);
    }


    @DataProvider(name = "parseableValues", parallel = true)
    private Object[][] parseableValues()
    {
        return new Object[][] {
                // spaces
                new Object[] { "1234 B", 1234, BYTE },
                new Object[] { "1234 kB", 1234, KILOBYTE },
                new Object[] { "1234 MB", 1234, MEGABYTE },
                new Object[] { "1234 GB", 1234, GIGABYTE },
                new Object[] { "1234 TB", 1234, TERABYTE },
                new Object[] { "1234 PB", 1234, PETABYTE },
                new Object[] { "1234.567 B", 1234.567, BYTE },
                new Object[] { "1234.567 kB", 1234.567, KILOBYTE },
                new Object[] { "1234.567 MB", 1234.567, MEGABYTE },
                new Object[] { "1234.567 GB", 1234.567, GIGABYTE },
                new Object[] { "1234.567 TB", 1234.567, TERABYTE },
                new Object[] { "1234.567 PB", 1234.567, PETABYTE },
                // no spaces
                new Object[] { "1234B", 1234, BYTE },
                new Object[] { "1234kB", 1234, KILOBYTE },
                new Object[] { "1234MB", 1234, MEGABYTE },
                new Object[] { "1234GB", 1234, GIGABYTE },
                new Object[] { "1234TB", 1234, TERABYTE },
                new Object[] { "1234PB", 1234, PETABYTE },
                new Object[] { "1234.567B", 1234.567, BYTE },
                new Object[] { "1234.567kB", 1234.567, KILOBYTE },
                new Object[] { "1234.567MB", 1234.567, MEGABYTE },
                new Object[] { "1234.567GB", 1234.567, GIGABYTE },
                new Object[] { "1234.567TB", 1234.567, TERABYTE },
                new Object[] { "1234.567PB", 1234.567, PETABYTE }
        };
    }

    @DataProvider(name = "printedValues", parallel = true)
    private Object[][] printedValues()
    {
        return new Object[][] {
                new Object[] { "1234B", 1234, BYTE },
                new Object[] { "1234kB", 1234, KILOBYTE },
                new Object[] { "1234MB", 1234, MEGABYTE },
                new Object[] { "1234GB", 1234, GIGABYTE },
                new Object[] { "1234TB", 1234, TERABYTE },
                new Object[] { "1234PB", 1234, PETABYTE },
                new Object[] { "1234.57B", 1234.567, BYTE },
                new Object[] { "1234.57kB", 1234.567, KILOBYTE },
                new Object[] { "1234.57MB", 1234.567, MEGABYTE },
                new Object[] { "1234.57GB", 1234.567, GIGABYTE },
                new Object[] { "1234.57TB", 1234.567, TERABYTE },
                new Object[] { "1234.57PB", 1234.567, PETABYTE }
        };
    }

    @DataProvider(name = "conversions", parallel = true)
    private Object[][] conversions()
    {
        return new Object[][] {
                new Object[] { BYTE, BYTE, 1 },
                new Object[] { BYTE, KILOBYTE, 1.0 / 1024 },
                new Object[] { BYTE, MEGABYTE, 1.0 / 1024 / 1024 },
                new Object[] { BYTE, GIGABYTE, 1.0 / 1024 / 1024 / 1024 },
                new Object[] { BYTE, TERABYTE, 1.0 / 1024 / 1024 / 1024 / 1024 },
                new Object[] { BYTE, PETABYTE, 1.0 / 1024 / 1024 / 1024 / 1024 / 1024 },

                new Object[] { KILOBYTE, BYTE, 1024 },
                new Object[] { KILOBYTE, KILOBYTE, 1 },
                new Object[] { KILOBYTE, MEGABYTE, 1.0 / 1024 },
                new Object[] { KILOBYTE, GIGABYTE, 1.0 / 1024 / 1024 },
                new Object[] { KILOBYTE, TERABYTE, 1.0 / 1024 / 1024 / 1024 },
                new Object[] { KILOBYTE, PETABYTE, 1.0 / 1024 / 1024 / 1024 / 1024 },

                new Object[] { MEGABYTE, BYTE, 1024 * 1024 },
                new Object[] { MEGABYTE, KILOBYTE, 1024 },
                new Object[] { MEGABYTE, MEGABYTE, 1 },
                new Object[] { MEGABYTE, GIGABYTE, 1.0 / 1024 },
                new Object[] { MEGABYTE, TERABYTE, 1.0 / 1024 / 1024 },
                new Object[] { MEGABYTE, PETABYTE, 1.0 / 1024 / 1024 / 1024 },

                new Object[] { GIGABYTE, BYTE, 1024 * 1024 * 1024 },
                new Object[] { GIGABYTE, KILOBYTE, 1024 * 1024 },
                new Object[] { GIGABYTE, MEGABYTE, 1024 },
                new Object[] { GIGABYTE, GIGABYTE, 1 },
                new Object[] { GIGABYTE, TERABYTE, 1.0 / 1024 },
                new Object[] { GIGABYTE, PETABYTE, 1.0 / 1024 / 1024 },

                new Object[] { TERABYTE, BYTE, 1024L * 1024 * 1024 * 1024 },
                new Object[] { TERABYTE, KILOBYTE, 1024 * 1024 * 1024 },
                new Object[] { TERABYTE, MEGABYTE, 1024 * 1024 },
                new Object[] { TERABYTE, GIGABYTE, 1024 },
                new Object[] { TERABYTE, TERABYTE, 1 },
                new Object[] { TERABYTE, PETABYTE, 1.0 / 1024 },

                new Object[] { PETABYTE, BYTE, 1024L * 1024 * 1024 * 1024 * 1024 },
                new Object[] { PETABYTE, KILOBYTE, 1024L * 1024 * 1024 * 1024 },
                new Object[] { PETABYTE, MEGABYTE, 1024 * 1024 * 1024 },
                new Object[] { PETABYTE, GIGABYTE, 1024 * 1024 },
                new Object[] { PETABYTE, TERABYTE, 1024 },
                new Object[] { PETABYTE, PETABYTE, 1 },
        };
    }
}
