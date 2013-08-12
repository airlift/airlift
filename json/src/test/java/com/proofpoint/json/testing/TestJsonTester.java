/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.json.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.proofpoint.json.testing.JsonTester.assertJsonEncode;

public class TestJsonTester
{

    private final SimpleEncoder simpleEncoder = new SimpleEncoder();
    private Map<String, Object> simpleExpected;
    private final ComplexEncoder complexEncoder = new ComplexEncoder();
    private Map<String, Object> complexExpected;

    @BeforeMethod
    public void setup()
    {
        simpleExpected = new HashMap<>();
        simpleExpected.put("s", "fred");
        simpleExpected.put("i", 3);
        simpleExpected.put("b", true);

        complexExpected = new HashMap<>();
        complexExpected.put("list", ImmutableList.of("a", "b", "a"));
        complexExpected.put("obj", simpleExpected);
    }

    @Test
    public void testEncodeSimple()
    {
        assertJsonEncode(simpleEncoder, simpleExpected);
    }

    @Test
    public void testEncodeDateTime()
    {
        assertJsonEncode(new DateTime(1376344694123L), "2013-08-12T21:58:14.123Z");
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testMissingField()
    {
       simpleExpected.put("extra", "field");
       assertJsonEncode(simpleEncoder, simpleExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testExtraField()
    {
       simpleExpected.remove("b");
       assertJsonEncode(simpleEncoder, simpleExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStringAsNumber()
    {
       simpleExpected.put("i", "3");
       assertJsonEncode(simpleEncoder, simpleExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStringAsBoolean()
    {
       simpleExpected.put("b", "true");
       assertJsonEncode(simpleEncoder, simpleExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testStringAsNull()
    {
       simpleExpected.put("nul", "null");
       assertJsonEncode(simpleEncoder, simpleExpected);
    }
    
    @Test
    public void testEncodeComplex()
    {
        assertJsonEncode(complexEncoder, complexExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testWrongListMember()
    {
        complexExpected.put("list", ImmutableList.of("a", "b", "b"));
        assertJsonEncode(complexEncoder, complexExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testWrongListOrder()
    {
        complexExpected.put("list", ImmutableList.of("a", "a", "b"));
        assertJsonEncode(complexEncoder, complexExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testMissingListMember()
    {
        complexExpected.put("list", ImmutableList.of("a", "b"));
        assertJsonEncode(complexEncoder, complexExpected);
    }

    @Test(expectedExceptions = AssertionError.class)
    public void testSubObjWithWrongValue()
    {
        simpleExpected.put("s", "wrong");
        assertJsonEncode(complexEncoder, complexExpected);
    }

    private static class SimpleEncoder
    {
        @JsonProperty
        private final String s = "fred";
        @JsonProperty
        private final int i = 3;
        @JsonProperty
        private final boolean b = true;
        @JsonProperty
        private final Integer nul = null;
    }

    private static class ComplexEncoder
    {
        @JsonProperty
        private final List<String> list = ImmutableList.of("a", "b", "a");
        @JsonProperty
        private final SimpleEncoder obj = new SimpleEncoder();
    }
}
