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
package io.airlift.event.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnusedDeclaration")
@EventType("NestedDummy")
public class NestedDummyEventClass
        extends FixedDummyEventClass
{
    private final List<String> strings;
    private final NestedPart nestedPart;
    private final List<NestedPart> nestedParts;

    public NestedDummyEventClass(String host, DateTime timestamp, UUID uuid,
            int intValue, String stringValue,
            List<String> strings,
            NestedPart nestedPart,
            List<NestedPart> nestedParts)
    {
        super(host, timestamp, uuid, intValue, stringValue);
        this.strings = strings;
        this.nestedPart = nestedPart;
        this.nestedParts = nestedParts;
    }

    @EventField
    public List<String> getStrings()
    {
        return strings;
    }

    @EventField
    public NestedPart getNestedPart()
    {
        return nestedPart;
    }

    @EventField
    public List<NestedPart> getNestedParts()
    {
        return nestedParts;
    }

    @EventField
    public Map<String, String> getNamedStrings()
    {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (String s : strings) {
            builder.put(s, s);
        }
        return builder.build();
    }

    @EventField
    public Multimap<String,String> getNamedStringList()
    {
        ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        for (String s : strings) {
            builder.putAll(s, s, s, s); // three copies
        }
        return builder.build();
    }

    @EventField
    public Map<String, NestedPart> getNamedParts()
    {
        ImmutableMap.Builder<String, NestedPart> builder = ImmutableMap.builder();
        for (NestedPart part : nestedParts) {
            builder.put(part.getName(), part);
        }
        return builder.build();
    }

    @EventType
    public static class NestedPart
    {
        private final String name;
        private final NestedPart part;

        public NestedPart(String name, NestedPart part)
        {
            this.name = name;
            this.part = part;
        }

        @EventField
        public String getName()
        {
            return name;
        }

        @EventField
        public NestedPart getPart()
        {
            return part;
        }
    }
}
