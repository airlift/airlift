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

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;

@TestInstance(PER_CLASS)
@Execution(SAME_THREAD)
public class TestPersonsResource {
    private PersonsResource resource;
    private PersonStore store;

    @BeforeEach
    public void setup() {
        store = new PersonStore(new StoreConfig());
        resource = new PersonsResource(store);
    }

    @Test
    public void testEmpty() {
        Response response = resource.listAll();
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(Collection.class);
        assertThat((Collection<?>) response.getEntity()).isEqualTo(new ArrayList<>());
    }

    @Test
    public void testListAll() {
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        Response response = resource.listAll();
        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        assertThat(response.getEntity()).isInstanceOf(Collection.class);
        assertThat((Collection<?>) response.getEntity())
                .isEqualTo(newArrayList(
                        new PersonRepresentation("foo@example.com", "Mr Foo", null),
                        new PersonRepresentation("bar@example.com", "Mr Bar", null)));
    }
}
