/**
 * Copyright (C) 2013 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * See the original at https://github.com/NessComputing/components-ness-core
 */
package io.airlift.json.uuid;

import org.testng.annotations.Test;

import java.util.UUID;

import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestUUIDs
{
    private static final String uuid = "6f32f693-c7b5-11e1-afa7-88af2abc9a66";
    private static final String caseSensitivity = "Dd000000-0000-0000-0000-000000000000";
    private static final String overflow = "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF";
    private static final String zero = "00000000-0000-0000-0000-000000000000";
    private static final String badLength = "00000000-f0000-0000-0000-000000000000";
    private static final String hyphen1 = "00000000f0000-0000-0000-000000000000";
    private static final String hyphen2 = "00000000-0000f0000-0000-000000000000";
    private static final String hyphen3 = "00000000-0000-0000f0000-000000000000";
    private static final String hyphen4 = "00000000-0000-0000-0000f000000000000";
    private static final String invalid1 = "00000000-0000-0-00-0000-000000000000";
    private static final String invalid2 = "0000g000-0000-0000-0000-000000000000";
    private static final String invalid3 = "00000000-00g0-0000-0000-000000000000";
    private static final String invalid4 = "00000000-0000-0g00-0000-000000000000";
    private static final String invalid5 = "00000000-0000-0000-00g0-000000000000";
    private static final String invalid6 = "00000000-0000-0000-0000-0000000000g0";

    @Test
    public void testBasic()
    {
        assertEquals(UUIDs.fromString(uuid), UUID.fromString(uuid));
        assertEquals(UUIDs.fromString(caseSensitivity), UUID.fromString(caseSensitivity));
        assertEquals(UUIDs.fromString(overflow), UUID.fromString(overflow));
        assertEquals(UUIDs.fromString(zero), UUID.fromString(zero));
    }

    @Test
    public void testLength()
    {
        UUID uuid = UUIDs.fromString(badLength);
        assertEquals(uuid.getMostSignificantBits(), 0);
        assertEquals(uuid.getLeastSignificantBits(), 0);
    }

    @Test
    public void testHyphen()
    {
        assertInvalidUuid(hyphen1);
        assertInvalidUuid(hyphen2);
        assertInvalidUuid(hyphen3);
        assertInvalidUuid(hyphen4);
    }

    @Test
    public void testInvalid()
    {
        assertInvalidUuid(invalid1);
        assertInvalidUuid(invalid2);
        assertInvalidUuid(invalid3);
        assertInvalidUuid(invalid4);
        assertInvalidUuid(invalid5);
        assertInvalidUuid(invalid6);
    }

    @Test
    public void testSimpleToString()
    {
        assertUuidToString("01234567-89ab-cdef-0000-0123456789ab");
        assertUuidToString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        assertUuidToString("00000000-0000-0000-0000-000000000000");
    }

    @Test
    public void testToString100()
    {
        long PRIME = 514229;
        for (long msb = 0; msb < 10; msb++) {
            for (long lsb = msb; lsb < msb + 10; lsb++) {
                UUID uuid = new UUID(msb * PRIME, lsb * PRIME * PRIME);
                assertEquals(UUIDs.toString(uuid), uuid.toString());
            }
        }
    }

    /*
     * Tests from http://svn.apache.org/viewvc/harmony/enhanced/java/branches/java6/classlib/modules/luni/src/test/api/common/org/apache/harmony/luni/tests/java/util/UUIDTest.java?revision=929252
     */

    @Test
    public void testFromString()
    {
        UUID actual = UUIDs.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
        UUID expected = new UUID(0xf81d4fae7dec11d0L, 0xa76500a0c91e6bf6L);
        assertEquals(actual, expected);
        assertEquals(actual.variant(), 2);
        assertEquals(actual.version(), 1);
        assertEquals(actual.timestamp(), 130742845922168750L);
        assertEquals(actual.clockSequence(), 10085);
        assertEquals(actual.node(), 690568981494L);

        actual = UUIDs.fromString("00000000-0000-1000-8000-000000000000");
        expected = new UUID(0x0000000000001000L, 0x8000000000000000L);
        assertEquals(actual, expected);
        assertEquals(actual.variant(), 2);
        assertEquals(actual.version(), 1);
        assertEquals(actual.timestamp(), 0L);
        assertEquals(actual.clockSequence(), 0);
        assertEquals(actual.node(), 0L);

        assertInvalidUuid("");

        // note dashes vs underscores
        assertInvalidUuid("f81d4fae_7dec-11d0-a765-00a0c91e6bf6");
        assertInvalidUuid("f81d4fae-7dec_11d0-a765-00a0c91e6bf6");
        assertInvalidUuid("f81d4fae-7dec-11d0_a765-00a0c91e6bf6");
        assertInvalidUuid("f81d4fae-7dec-11d0-a765_00a0c91e6bf6");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testFromStringNull()
    {
        UUIDs.fromString(null);
    }

    @Test
    public void testFromStringInvalid()
    {
        assertInvalidUuid("0-0-0-0-");
        assertInvalidUuid("-0-0-0-0-0");
        assertInvalidUuid("-0-0-0-0");
        assertInvalidUuid("-0-0-0-");
        assertInvalidUuid("0--0-0-0");
        assertInvalidUuid("0-0-0-0-");
        assertInvalidUuid("-1-0-0-0-0");

        UUID uuid = UUIDs.fromString("0-0-0-0-0");
        assertEquals(uuid.getMostSignificantBits(), 0);
        assertEquals(uuid.getLeastSignificantBits(), 0);

        uuid = UUIDs.fromString("123456789-0-0-0-0");
        assertEquals(uuid.getMostSignificantBits(), 0x2345678900000000L);
        assertEquals(uuid.getLeastSignificantBits(), 0x0L);

        uuid = UUIDs.fromString("111123456789-0-0-0-0");
        assertEquals(uuid.getMostSignificantBits(), 0x2345678900000000L);
        assertEquals(uuid.getLeastSignificantBits(), 0x0L);

        uuid = UUIDs.fromString("7fffffffffffffff-0-0-0-0");
        assertEquals(uuid.getMostSignificantBits(), 0xffffffff00000000L);
        assertEquals(uuid.getLeastSignificantBits(), 0x0L);

        uuid = UUID.fromString("7fffffffffffffff-7fffffffffffffff-7fffffffffffffff-0-0");
        assertEquals(uuid.getMostSignificantBits(), 0xffffffffffffffffL);
        assertEquals(uuid.getLeastSignificantBits(), 0x0L);

        uuid = UUIDs.fromString("0-0-0-7fffffffffffffff-7fffffffffffffff");
        assertEquals(uuid.getMostSignificantBits(), 0x0L);
        assertEquals(uuid.getLeastSignificantBits(), 0xffffffffffffffffL);

        assertInvalidUuidNumberFormat("8000000000000000-0-0-0-0");
        assertInvalidUuidNumberFormat("0-0-0-8000000000000000-0");
        assertInvalidUuidNumberFormat("0-0-0-0-8000000000000000");
    }

    @Test
    public void testToString()
    {
        UUID uuid = new UUID(0xf81d4fae7dec11d0L, 0xa76500a0c91e6bf6L);
        assertEquals(UUIDs.toString(uuid), "f81d4fae-7dec-11d0-a765-00a0c91e6bf6");

        uuid = new UUID(0x0000000000001000L, 0x8000000000000000L);
        assertEquals(UUIDs.toString(uuid), "00000000-0000-1000-8000-000000000000");
    }

    private static void assertUuidToString(String s)
    {
        assertEquals(UUIDs.toString(UUID.fromString(s)), s);
    }

    private static void assertInvalidUuid(String s)
    {
        try {
            UUIDs.fromString(s);
            fail("Expected IllegalArgumentException: " + s);
        }
        catch (IllegalArgumentException ignored) {
        }
    }

    private static void assertInvalidUuidNumberFormat(String s)
    {
        try {
            UUIDs.fromString(s);
            fail("Expected IllegalArgumentException with a NumberFormatException cause");
        }
        catch (IllegalArgumentException e) {
            assertInstanceOf(e.getCause(), NumberFormatException.class);
        }
    }
}
