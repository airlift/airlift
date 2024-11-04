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

import io.airlift.jaxrs.testing.MockUriInfo;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestPersonResource
{
    private PersonResource resource;
    private PersonStore store;

    @BeforeEach
    public void setup()
    {
        store = new PersonStore(new StoreConfig());
        resource = new PersonResource(store);
    }

    @Test
    public void testNotFound()
    {
        Response response = resource.get("foo", MockUriInfo.from(URI.create("http://localhost/v1/person/1")));
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGet()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.get("foo", MockUriInfo.from(URI.create("http://localhost/v1/person/1")));
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isEqualTo(new PersonRepresentation("foo@example.com", "Mr Foo", URI.create("http://localhost/v1/person/1")));
        assertThat(response.getMetadata().get("Content-Type")).isNull(); // content type is set by JAX-RS based on @Produces
    }

    @Test
    public void testGetNull()
    {
        assertThatThrownBy(() -> resource.get(null, MockUriInfo.from(URI.create("http://localhost/v1/person/1"))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testAdd()
    {
        Response response = resource.put("foo", new PersonRepresentation("foo@example.com", "Mr Foo", null));

        assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
        assertThat(response.getEntity()).isNull();
        assertThat(response.getMetadata().get("Content-Type")).isNull(); // content type is set by JAX-RS based on @Produces

        assertThat(store.get("foo")).isEqualTo(new Person("foo@example.com", "Mr Foo"));
    }

    @Test
    public void testPutNullId()
    {
        assertThatThrownBy(() -> resource.put(null, new PersonRepresentation("foo@example.com", "Mr Foo", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testPutNullValue()
    {
        assertThatThrownBy(() -> resource.put("foo", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testReplace()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.put("foo", new PersonRepresentation("bar@example.com", "Mr Bar", null));

        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        assertThat(response.getEntity()).isNull();
        assertThat(response.getMetadata().get("Content-Type")).isNull(); // content type is set by JAX-RS based on @Produces

        assertThat(store.get("foo")).isEqualTo(new Person("bar@example.com", "Mr Bar"));
    }

    @Test
    public void testDelete()
    {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        Response response = resource.delete("foo");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
        assertThat(response.getEntity()).isNull();

        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testDeleteMissing()
    {
        Response response = resource.delete("foo");
        assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
        assertThat(response.getEntity()).isNull();
    }

    @Test
    public void testDeleteNullId()
    {
        assertThatThrownBy(() -> resource.delete(null))
                .isInstanceOf(NullPointerException.class);
    }
}
