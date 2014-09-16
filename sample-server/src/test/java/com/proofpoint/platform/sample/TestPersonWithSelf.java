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
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.json.testing.JsonTester.assertJsonEncode;
import static com.proofpoint.platform.sample.Person.createPerson;
import static com.proofpoint.platform.sample.PersonWithSelf.createPersonWithSelf;
import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

public class TestPersonWithSelf
{
    @Test
    public void testEquivalence()
    {
        Person fooPerson = createPerson("foo@example.com", "Mr Foo");
        Person fooPerson2 = createPerson("foo@example.com", "Mr Foo");
        Person barPerson = createPerson("bar@example.com", "Mr Bar");
        Person barPerson2 = createPerson("bar@example.com", "Mr Bar");
        equivalenceTester()
                .addEquivalentGroup(createPersonWithSelf(fooPerson, URI.create("http://example.com")), createPersonWithSelf(fooPerson2, URI.create("http://example.com")))
                .addEquivalentGroup(createPersonWithSelf(fooPerson, URI.create("https://example.com")), createPersonWithSelf(fooPerson2, URI.create("https://example.com")))
                .addEquivalentGroup(createPersonWithSelf(barPerson, URI.create("http://example.com")), createPersonWithSelf(barPerson2, URI.create("http://example.com")))
                .addEquivalentGroup(createPersonWithSelf(barPerson, URI.create("https://example.com")), createPersonWithSelf(barPerson2, URI.create("https://example.com")))
                .check();
    }

    @Test
    public void testJsonEncode()
    {
        assertJsonEncode(createPersonWithSelf(createPerson("alice@example.com", "Alice"), URI.create("http://example.com/foo")),
                ImmutableMap.of(
                        "self", "http://example.com/foo",
                        "name", "Alice",
                        "email", "alice@example.com"
                ));
    }
}
