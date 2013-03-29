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
package com.proofpoint.platform.sample;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.proofpoint.json.JsonCodec;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Map;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.json.testing.JsonTester.decodeJson;
import static com.proofpoint.testing.ValidationAssertions.assertFailsValidation;
import static com.proofpoint.testing.ValidationAssertions.assertValidates;
import static org.testng.Assert.assertEquals;

public class TestPersonRepresentation
{
    private final JsonCodec<PersonRepresentation> codec = jsonCodec(PersonRepresentation.class);
    private Map<String,String> map;

    @BeforeMethod
    public void setup() {
        map = Maps.newHashMap(ImmutableMap.of(
                "name", "Mr Foo",
                "email", "foo@example.com"));
    }

    @Test
    public void testJsonDecode()
    {
        PersonRepresentation personRepresentation = decodeJson(codec, map);
        assertValidates(personRepresentation);
        assertEquals(personRepresentation.toPerson(), new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testNoEmail()
    {
        map.remove("email");
        assertFailsValidation(decodeJson(codec, map), "email", "is missing", NotNull.class);
    }

    @Test
    public void testInvalidEmail()
    {
        map.put("email", "invalid");
        assertFailsValidation(decodeJson(codec, map), "email", "is malformed", Pattern.class);
    }

    @Test
    public void testNoName()
    {
        map.remove("name");
        assertFailsValidation(decodeJson(codec, map), "name", "is missing", NotNull.class);
    }
}
