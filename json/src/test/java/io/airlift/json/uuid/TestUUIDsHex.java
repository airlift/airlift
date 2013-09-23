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

import static io.airlift.json.uuid.UUIDs.getNibbleFromChar;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestUUIDsHex
{
    private static final char[] numChars = new char[] {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private static final char[] alphaLowChars = new char[] {'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] alphaHiChars = new char[] {'A', 'B', 'C', 'D', 'E', 'F'};

    @SuppressWarnings({"CharUsedInArithmeticContext", "ImplicitNumericConversion"})
    private static final char[] illegalChars = new char[] {'0' - 1, '9' + 1, 'a' - 1, 'f' + 1, 'A' - 1, 'F' + 1};

    @Test
    public void testNumChars()
    {
        for (int i = 0; i < numChars.length; i++) {
            assertEquals(getNibbleFromChar(numChars[i]), i);
        }
    }

    @Test
    public void testAlphaLowChars()
    {
        for (int i = 0; i < alphaLowChars.length; i++) {
            assertEquals(getNibbleFromChar(alphaLowChars[i]), i + 10);
        }
    }

    @Test
    public void testAlphaHiChars()
    {
        for (int i = 0; i < alphaHiChars.length; i++) {
            assertEquals(getNibbleFromChar(alphaHiChars[i]), i + 10);
        }
    }

    @Test
    public void testBoundaryChars()
    {
        for (char illegalChar : illegalChars) {
            try {
                getNibbleFromChar(illegalChar);
                fail("Expected IllegalArgumentException for character: " + illegalChar);
            }
            catch (IllegalArgumentException ignored) {
            }
        }
    }
}
