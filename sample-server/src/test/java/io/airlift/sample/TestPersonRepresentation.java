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
package io.airlift.sample;

import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import static io.airlift.json.JsonCodec.jsonCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TestPersonRepresentation
{
    private final JsonCodec<PersonRepresentation> codec = jsonCodec(PersonRepresentation.class);

    // TODO: add equivalence test

    @Test
    public void testJsonRoundTrip()
    {
        PersonRepresentation expected = new PersonRepresentation("alice@example.com", "Alice", null);
        String json = codec.toJson(expected);
        PersonRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        PersonRepresentation expected = new PersonRepresentation("foo@example.com", "Mr Foo", null);

        String json = Resources.toString(Resources.getResource("single.json"), UTF_8);
        PersonRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
