package com.proofpoint.event.client;

import org.joda.time.DateTime;

import java.util.List;
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
