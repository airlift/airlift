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

@SuppressWarnings("UnusedDeclaration")
@EventType("ChainedCircular")
public class ChainedCircularEventClass
{
    private final ChainedPart part;

    public ChainedCircularEventClass(ChainedPart part)
    {
        this.part = part;
    }

    @EventField
    public ChainedPart getPart()
    {
        return part;
    }

    @EventType
    public static class ChainedPart
    {
        private final String name;
        private ChainedPart part;

        public ChainedPart(String name)
        {
            this.name = name;
        }

        public void setPart(ChainedPart part)
        {
            this.part = part;
        }

        @EventField
        public String getName()
        {
            return name;
        }

        @EventField
        public ChainedPart getPart()
        {
            return part;
        }

        @Override
        public String toString()
        {
            return String.format("ChainedPart{%s}", name);
        }
    }
}
