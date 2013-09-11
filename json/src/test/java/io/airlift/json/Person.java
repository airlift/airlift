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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class Person
{
    private String name;
    private boolean rocks;

    public static void validatePersonJsonCodec(JsonCodec<Person> jsonCodec)
    {
        Person expected = new Person().setName("dain").setRocks(true);

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }

    public static void validatePersonListJsonCodec(JsonCodec<List<Person>> jsonCodec)
    {
        ImmutableList<Person> expected = ImmutableList.of(
                new Person().setName("dain").setRocks(true),
                new Person().setName("martin").setRocks(true),
                new Person().setName("mark").setRocks(true));

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }

    public static void validatePersonMapJsonCodec(JsonCodec<Map<String, Person>> jsonCodec)
    {
        ImmutableMap<String, Person> expected = ImmutableMap.<String, Person>builder()
                .put("dain", new Person().setName("dain").setRocks(true))
                .put("martin", new Person().setName("martin").setRocks(true))
                .put("mark", new Person().setName("mark").setRocks(true))
                .build();

        String json = jsonCodec.toJson(expected);
        assertEquals(jsonCodec.fromJson(json), expected);

        byte[] bytes = jsonCodec.toJsonBytes(expected);
        assertEquals(jsonCodec.fromJson(bytes), expected);
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    @JsonProperty
    public Person setName(String name)
    {
        this.name = name;
        return this;
    }

    @JsonProperty
    public boolean isRocks()
    {
        return rocks;
    }

    @JsonProperty
    public Person setRocks(boolean rocks)
    {
        this.rocks = rocks;
        return this;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        Person o = (Person) obj;
        return Objects.equal(this.name, o.name) &&
                Objects.equal(this.rocks, o.rocks);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(name, rocks);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("rocks", rocks)
                .toString();
    }
}
