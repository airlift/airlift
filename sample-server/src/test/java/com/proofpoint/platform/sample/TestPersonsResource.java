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
import com.proofpoint.event.client.NullEventClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.platform.sample.Person.createPerson;
import static org.testng.Assert.assertEquals;

public class TestPersonsResource
{
    private PersonsResource resource;
    private PersonStore store;

    @BeforeMethod
    public void setup()
    {
        store = new PersonStore(new StoreConfig(), new NullEventClient());
        resource = new PersonsResource(store);
    }

    @Test
    public void testEmpty()
    {
        assertEquals(resource.listAll(), ImmutableMap.of());
    }

    @Test
    public void testListAll()
    {
        store.put("foo", createPerson("foo@example.com", "Mr Foo"));
        store.put("bar", createPerson("bar@example.com", "Mr Bar"));

        assertEquals(resource.listAll(), ImmutableMap.of(
                "foo", createPerson("foo@example.com", "Mr Foo"),
                "bar", createPerson("bar@example.com", "Mr Bar")
        ));
    }
}
