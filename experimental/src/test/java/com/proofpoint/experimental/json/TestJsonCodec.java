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
package com.proofpoint.experimental.json;

import com.google.inject.TypeLiteral;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static com.proofpoint.experimental.json.JsonCodec.*;

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
    public void testTypeLiteralList()
            throws Exception
    {
        JsonCodec<List<Person>> jsonCodec = jsonCodec(new TypeLiteral<List<Person>>() { });

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
        JsonCodec<Map<String, Person>> jsonCodec = jsonCodec(new TypeLiteral<Map<String, Person>>() {});

        Person.validatePersonMapJsonCodec(jsonCodec);
    }

}
