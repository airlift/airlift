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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.airlift.units.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class TestPersonStore {
    @Test
    public void testStartsEmpty() {
        PersonStore store = new PersonStore(new StoreConfig());
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    public void testTtl() throws InterruptedException {
        StoreConfig config = new StoreConfig();
        config.setTtl(new Duration(1, TimeUnit.MILLISECONDS));

        PersonStore store = new PersonStore(config);
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        Thread.sleep(2);
        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testPut() {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        assertThat(new Person("foo@example.com", "Mr Foo")).isEqualTo(store.get("foo"));
        assertThat(store.getAll().size()).isEqualTo(1);
    }

    @Test
    public void testIdempotentPut() {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("foo", new Person("foo@example.com", "Mr Bar"));

        assertThat(new Person("foo@example.com", "Mr Bar")).isEqualTo(store.get("foo"));
        assertThat(store.getAll().size()).isEqualTo(1);
    }

    @Test
    public void testDelete() {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.delete("foo");

        assertThat(store.get("foo")).isNull();
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    public void testIdempotentDelete() {
        PersonStore store = new PersonStore(new StoreConfig());
        store.put("foo", new Person("foo@example.com", "Mr Foo"));

        store.delete("foo");
        assertThat(store.getAll()).isEmpty();
        assertThat(store.get("foo")).isNull();

        store.delete("foo");
        assertThat(store.getAll()).isEmpty();
        assertThat(store.get("foo")).isNull();
    }

    @Test
    public void testGetAll() {
        PersonStore store = new PersonStore(new StoreConfig());

        store.put("foo", new Person("foo@example.com", "Mr Foo"));
        store.put("bar", new Person("bar@example.com", "Mr Bar"));

        assertThat(store.getAll().size()).isEqualTo(2);
        assertThat(store.getAll())
                .isEqualTo(asList(new Person("foo@example.com", "Mr Foo"), new Person("bar@example.com", "Mr Bar")));
    }
}
