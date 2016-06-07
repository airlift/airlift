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

import com.google.common.base.Joiner;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.event.client.EventTypeMetadata.getEventTypeMetadata;
import static io.airlift.event.client.EventTypeMetadata.getEventTypeMetadataNested;
import static io.airlift.event.client.EventTypeMetadata.getValidEventTypeMetadata;
import static io.airlift.testing.Assertions.assertContains;
import static org.testng.Assert.assertEquals;

public class TestEventValidation
{
    @Test
    public void testLegacyEventTypeName()
    {
        getValidEventTypeMetadata(DummyEventClass.class);
    }

    @Test
    public void testInvalidEventTypeName()
    {
        @EventType("junk!")
        class TestEvent
        {}

        assertInvalidEvent(TestEvent.class, "Event name is invalid");
    }

    @Test
    public void testMissingEventTypeAnnotation()
    {
        class TestEvent
        {}

        assertInvalidEvent(TestEvent.class, "is not annotated with");
    }

    @Test
    public void testMissingEventTypeName()
    {
        @EventType
        class TestEvent
        {}

        assertInvalidEvent(TestEvent.class, "does not specify an event name");
    }

    @Test
    public void testEventTypeNameForNestedEvent()
    {
        @EventType("Test")
        class TestEvent
        {
            @EventType("NestedCustom")
            class Nested
            {}

            @EventField
            public Nested getNested()
            {
                return null;
            }

            @EventType
            class Nested2
            {}

            @EventField
            public Nested2 getNested2()
            {
                return null;
            }
        }

        assertEquals(getEventTypeMetadataNested(TestEvent.Nested.class).getTypeName(), "NestedCustom");
        assertEquals(getEventTypeMetadataNested(TestEvent.Nested2.class).getTypeName(), TestEvent.Nested2.class.getSimpleName());
    }

    private static void assertInvalidEvent(Class<?> eventClass, String errorPart)
    {
        EventTypeMetadata<?> metadata = getEventTypeMetadata(eventClass);
        List<String> errors = metadata.getErrors();
        assertEquals(errors.size(), 1, "expected exactly one error:\n" + Joiner.on('\n').join(errors));
        assertContains(errors.get(0), errorPart);
    }
}
