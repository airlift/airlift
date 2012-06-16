package io.airlift.event.client;

import com.google.common.base.Joiner;
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.event.client.EventTypeMetadata.getEventTypeMetadata;
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
            @EventType("Nested")
            class Nested
            {}

            @EventField
            public Nested getNested()
            {
                return null;
            }
        }

        assertInvalidEvent(TestEvent.class, "specifies an event name but is used as a nested event");
    }

    private static void assertInvalidEvent(Class<?> eventClass, String errorPart)
    {
        EventTypeMetadata<?> metadata = getEventTypeMetadata(eventClass);
        List<String> errors = metadata.getErrors();
        assertEquals(errors.size(), 1, "expected exactly one error:\n" + Joiner.on('\n').join(errors));
        assertContains(errors.get(0), errorPart);
    }
}
