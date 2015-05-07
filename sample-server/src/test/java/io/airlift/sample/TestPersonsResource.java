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

import io.airlift.event.client.NullEventClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;

import java.util.Collection;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.testing.Assertions.assertInstanceOf;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
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
        Response response = resource.listAll();
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList());
    }

    @Test
    public void testListAll()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        Response response = resource.listAll();
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertInstanceOf(response.getEntity(), Collection.class);
        assertEquals((Collection<?>) response.getEntity(), newArrayList(
                new PersonRepresentation("foo@example.com", "Mr Foo", null),
                new PersonRepresentation("bar@example.com", "Mr Bar", null)
        ));
    }


}
