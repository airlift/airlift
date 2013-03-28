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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.Assert;

import java.util.List;
import java.util.Map;

public class ImmutablePerson
{
    private final String name;
    private final boolean rocks;
    private final String notWritable = null;

    public static void validatePersonJsonCodec(JsonCodec<ImmutablePerson> jsonCodec)
    {
        ImmutablePerson expected = new ImmutablePerson("dain", true);
        String json = jsonCodec.toJson(expected);
        ImmutablePerson actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
    }

    public static void validatePersonListJsonCodec(JsonCodec<List<ImmutablePerson>> jsonCodec)
    {
        ImmutableList<ImmutablePerson> expected = ImmutableList.of(
                new ImmutablePerson("dain", true),
                new ImmutablePerson("martin", true),
                new ImmutablePerson("mark",true));

        String json = jsonCodec.toJson(expected);
        List<ImmutablePerson> actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
    }

    public static void validatePersonMapJsonCodec(JsonCodec<Map<String, ImmutablePerson>> jsonCodec)
    {
        ImmutableMap<String, ImmutablePerson> expected = ImmutableMap.<String, ImmutablePerson>builder()
                .put("dain", new ImmutablePerson("dain", true))
                .put("martin", new ImmutablePerson("martin", true))
                .put("mark", new ImmutablePerson("mark", true))
                .build();

        String json = jsonCodec.toJson(expected);
        Map<String, ImmutablePerson> actual = jsonCodec.fromJson(json);
        Assert.assertEquals(actual, expected);
    }

    @JsonCreator
    public ImmutablePerson(
            @JsonProperty("name") String name,
            @JsonProperty("rocks") boolean rocks)
    {
        this.name = name;
        this.rocks = rocks;
    }

    @JsonProperty
    public String getName()
    {
        return name;
    }

    @JsonProperty
    public boolean isRocks()
    {
        return rocks;
    }

    @JsonProperty
    public String getNotWritable()
    {
        return notWritable;
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

        ImmutablePerson person = (ImmutablePerson) o;

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
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("rocks", rocks)
                .toString();
    }
}
