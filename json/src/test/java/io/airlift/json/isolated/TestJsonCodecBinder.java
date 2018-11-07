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
package io.airlift.json.isolated;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecBinder;
import io.airlift.json.JsonModule;
import io.airlift.json.Person;
import org.testng.annotations.Test;

import javax.inject.Inject;

import java.util.List;
import java.util.Map;

import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static org.testng.Assert.assertNotNull;

public class TestJsonCodecBinder
{
    @Inject
    protected JsonCodec<Person> personJsonCodec;
    @Inject
    protected JsonCodec<List<Person>> personListJsonCodec;
    @Inject
    protected JsonCodec<Map<String, Person>> personMapJsonCodec;

    @Test
    public void test()
            throws Exception
    {
        Injector injector = Guice.createInjector(new JsonModule(),
                binder -> {
                    JsonCodecBinder codecBinder = jsonCodecBinder(binder);
                    codecBinder.bindJsonCodec(Person.class);
                    codecBinder.bindListJsonCodec(Person.class);
                    codecBinder.bindMapJsonCodec(String.class, Person.class);
                });

        injector.injectMembers(this);

        assertNotNull(personJsonCodec);
        assertNotNull(personListJsonCodec);
        assertNotNull(personMapJsonCodec);

        Person.validatePersonJsonCodec(personJsonCodec);
        Person.validatePersonListJsonCodec(personListJsonCodec);
        Person.validatePersonMapJsonCodec(personMapJsonCodec);
    }
}
