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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;

import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A class that provides an alternate implementation of {@link
 * UUID#fromString(String)} and {@link UUID#toString()}.
 * <p/>
 * The version in the JDK uses {@link String#split(String)}
 * which does not compile the regular expression that is used for splitting
 * the UUID string and results in the allocation of multiple strings in a
 * string array. This class is faster and avoids performance issues caused
 * by increased garbage produced by the JDK class.
 */
class UUIDs
{
    private UUIDs() {}

    public static UUID fromString(String str)
    {
        try {
            int dashCount = 4;
            int[] dashPos = new int[6];
            dashPos[0] = -1;
            dashPos[5] = str.length();

            for (int i = str.length() - 1; i >= 0; i--) {
                if (str.charAt(i) == '-') {
                    checkArgument(dashCount > 0, "Too many dashes (-)");
                    dashPos[dashCount] = i;
                    dashCount--;
                }
            }
            checkArgument(dashCount == 0, "Not enough dashes");

            long mostSigBits = decode(str, dashPos, 0) & 0xffffffffL;
            mostSigBits <<= 16;
            mostSigBits |= (decode(str, dashPos, 1) & 0xffffL);
            mostSigBits <<= 16;
            mostSigBits |= (decode(str, dashPos, 2) & 0xffffL);

            long leastSigBits = (decode(str, dashPos, 3) & 0xffffL);
            leastSigBits <<= 48;
            leastSigBits |= (decode(str, dashPos, 4) & 0xffffffffffffL);

            return new UUID(mostSigBits, leastSigBits);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID string: " + str, e);
        }
    }

    private static long decode(String str, int[] dashPos, int field)
    {
        int start = dashPos[field] + 1;
        int end = dashPos[field + 1];
        checkArgument(start < end, "In call to decode(), start (%d) >= end (%d)", start, end);
        long n = 0;
        for (int i = start; i < end; i++) {
            int x = getNibbleFromChar(str.charAt(i));
            n <<= 4;
            if (n < 0) {
                throw new NumberFormatException("long overflow");
            }
            n |= x;
        }
        return n;
    }

    @VisibleForTesting
    @SuppressWarnings("CharUsedInArithmeticContext")
    static int getNibbleFromChar(char c)
    {
        if ((c >= '0') && (c <= '9')) {
            return c - '0';
        }
        if ((c >= 'a') && (c <= 'f')) {
            return 10 + (c - 'a');
        }
        if ((c >= 'A') && (c <= 'F')) {
            return 10 + (c - 'A');
        }
        throw new IllegalArgumentException(c + " is not a valid hex character");
    }

    public static String toString(UUID uuid)
    {
        return toString(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    /**
     * Roughly patterned (read: stolen) from java.util.UUID and java.lang.Long.
     */
    public static String toString(long msb, long lsb)
    {
        char[] uuidChars = new char[36];

        digits(uuidChars, 0, 8, msb >> 32);
        uuidChars[8] = '-';
        digits(uuidChars, 9, 4, msb >> 16);
        uuidChars[13] = '-';
        digits(uuidChars, 14, 4, msb);
        uuidChars[18] = '-';
        digits(uuidChars, 19, 4, lsb >> 48);
        uuidChars[23] = '-';
        digits(uuidChars, 24, 12, lsb);

        return new String(uuidChars);
    }

    private static void digits(char[] dest, int offset, int digits, long val)
    {
        int shift = digits * 4;
        long hi = 1L << shift;
        toUnsignedString(dest, offset, digits, hi | (val & (hi - 1)));
    }

    private static final String DIGITS = "0123456789abcdef";

    private static void toUnsignedString(char[] dest, int offset, int len, long value)
    {
        int charPos = len;
        do {
            charPos--;
            dest[offset + charPos] = DIGITS.charAt(Ints.checkedCast(value & 15));
            value >>>= 4;
        }
        while ((value != 0) && (charPos > 0));
    }
}
