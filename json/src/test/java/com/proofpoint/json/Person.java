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
package com.proofpoint.json;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.codehaus.jackson.annotate.JsonProperty;
import org.testng.Assert;

import java.util.List;
import java.util.Map;

public class Person
{
    private String name;
    private boolean rocks;

    public static void validatePersonJsonCodec(JsonCodec<Person> jsonCodec)
    {
        Person expected = new Person().setName("dain").setRocks(true);
        String json = jsonCodec.toJson(expected);
        Person actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
    }

    public static void validatePersonListJsonCodec(JsonCodec<List<Person>> jsonCodec)
    {
        ImmutableList<Person> expected = ImmutableList.of(
                new Person().setName("dain").setRocks(true),
                new Person().setName("martin").setRocks(true),
                new Person().setName("mark").setRocks(true));

        String json = jsonCodec.toJson(expected);
        List<Person> actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
    }

    public static void validatePersonMapJsonCodec(JsonCodec<Map<String, Person>> jsonCodec)
    {
        ImmutableMap<String, Person> expected = ImmutableMap.<String, Person>builder()
                .put("dain", new Person().setName("dain").setRocks(true))
                .put("martin", new Person().setName("martin").setRocks(true))
                .put("mark", new Person().setName("mark").setRocks(true))
                .build();

        String json = jsonCodec.toJson(expected);
        Map<String, Person> actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
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
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Person person = (Person) o;

        if (rocks != person.rocks) {
            return false;
        }
        if (name != null ? !name.equals(person.name) : person.name != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (rocks ? 1 : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Person");
        sb.append("{name='").append(name).append('\'');
        sb.append(", rocks=").append(rocks);
        sb.append('}');
        return sb.toString();
    }
}
