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
package io.airlift.json;

import com.google.common.reflect.TypeToken;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.json.JsonCodec.listJsonCodec;
import static io.airlift.json.JsonCodec.mapJsonCodec;

public class TestJsonCodec
{
    @Test
    public void testJsonCodec()
            throws Exception
    {
        JsonCodec<Person> jsonCodec = jsonCodec(Person.class);

        Person.validatePersonJsonCodec(jsonCodec);
    }

    @Test
    public void testListJsonCodec()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(Person.class);

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testListJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = listJsonCodec(jsonCodec(Person.class));

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testTypeTokenList()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = jsonCodec(new TypeToken<List<Person>>() {});

        Person.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testMapJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, Person.class);

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testMapJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = mapJsonCodec(String.class, jsonCodec(Person.class));

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testTypeLiteralMap()
            throws Exception
    {
        JsonCodec<Map<String, Person>> jsonCodec = jsonCodec(new TypeToken<Map<String, Person>>() {});

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableJsonCodec()
            throws Exception
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonJsonCodec(jsonCodec);
    }

    // todo this should not throw an exception, but jackson will try to write to the private final field. remove this when jackson is fixed
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testAsymmetricJsonCodec()
            throws Exception
    {
        JsonCodec<ImmutablePerson> jsonCodec = jsonCodec(ImmutablePerson.class);
        jsonCodec.fromJson("{ \"notWritable\": \"foo\" }");
    }

    @Test
    public void testImmutableListJsonCodec()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(ImmutablePerson.class);

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableListJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = listJsonCodec(jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenList()
            throws Exception
    {
        JsonCodec<List<ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<List<ImmutablePerson>>() {});

        ImmutablePerson.validatePersonListJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, ImmutablePerson.class);

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableMapJsonCodecFromJsonCodec()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = mapJsonCodec(String.class, jsonCodec(ImmutablePerson.class));

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }

    @Test
    public void testImmutableTypeTokenMap()
            throws Exception
    {
        JsonCodec<Map<String, ImmutablePerson>> jsonCodec = jsonCodec(new TypeToken<Map<String, ImmutablePerson>>() {});

        ImmutablePerson.validatePersonMapJsonCodec(jsonCodec);
    }
}
