/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.JsonCodec.listJsonCodec;
import static org.testng.Assert.assertEquals;

public abstract class AbstractCodecBodyGeneratorTest
{
    protected abstract <T> BodyGenerator createBodyGenerator(JsonCodec<T> jsonCodec, T instance);

    protected abstract Object decodeBody(byte[] body)
            throws Exception;

    @Test
    public void testEncodeSimple()
            throws Exception
    {
        BodyGenerator bodyGenerator = createBodyGenerator(jsonCodec(JsonClass.class),
                new JsonClass("first", "second"));
        ImmutableMap<String, String> expected = ImmutableMap.of(
                "firstField", "first",
                "secondField", "second"
        );
        assertEncodes(bodyGenerator, expected);
    }

    @Test
    public void testEncodeList()
            throws Exception
    {
        BodyGenerator bodyGenerator = createBodyGenerator(listJsonCodec(JsonClass.class),
                ImmutableList.of(new JsonClass("first", "second"), new JsonClass("third", "fourth")));
        ImmutableList<ImmutableMap<String, String>> expected = ImmutableList.of(
                ImmutableMap.of(
                        "firstField", "first",
                        "secondField", "second"
                ),
                ImmutableMap.of(
                        "firstField", "third",
                        "secondField", "fourth"
                ));
        assertEncodes(bodyGenerator, expected);
    }

    private void assertEncodes(BodyGenerator bodyGenerator, Object expected)
            throws Exception
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bodyGenerator.write(outputStream);
        assertEquals(decodeBody(outputStream.toByteArray()), expected);

        ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
        bodyGenerator.write(outputStream2);
        assertEquals(outputStream2.toByteArray(), outputStream.toByteArray());
    }

    public static class JsonClass
    {
        @JsonProperty("firstField")
        private String firstField;

        @JsonProperty("secondField")
        private String secondField;

        JsonClass(String firstField, String secondField)
        {
            this.firstField = firstField;
            this.secondField = secondField;
        }
    }
}
