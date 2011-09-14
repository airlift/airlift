package com.proofpoint.event.client;

import com.google.common.base.Joiner;
import org.testng.annotations.Test;

import java.util.List;

import static com.proofpoint.event.client.EventTypeMetadata.getEventTypeMetadata;
import static com.proofpoint.event.client.EventTypeMetadata.getValidEventTypeMetadata;
import static com.proofpoint.testing.Assertions.assertContains;
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

    private static void assertInvalidEvent(Class<?> eventClass, String errorPart)
    {
        EventTypeMetadata<?> metadata = getEventTypeMetadata(eventClass);
        List<String> errors = metadata.getErrors();
        assertEquals(errors.size(), 1, "expected exactly one error:\n" + Joiner.on('\n').join(errors));
        assertContains(errors.get(0), errorPart);
    }
}
