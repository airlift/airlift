package io.airlift.event.client;

import org.joda.time.DateTime;

import java.util.UUID;

@EventType("FixedDummy")
public class FixedDummyEventClass
{
    private final String host;
    private final DateTime timestamp;
    private final UUID uuid;
    private int intValue;
    private final String stringValue;

    public FixedDummyEventClass(String host, DateTime timestamp, UUID uuid, int intValue, String stringValue)
    {
        this.host = host;
        this.timestamp = timestamp;
        this.uuid = uuid;
        this.intValue = intValue;
        this.stringValue = stringValue;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.HOST)
    public String getHost()
    {
        return host;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.TIMESTAMP)
    public DateTime getTimestamp()
    {
        return timestamp;
    }

    @EventField(fieldMapping = EventField.EventFieldMapping.UUID)
    public UUID getUuid()
    {
        return uuid;
    }

    @EventField
    public int getIntValue()
    {
        return intValue;
    }

    @EventField
    public String getStringValue()
    {
        return stringValue;
    }

    @EventField
    public String getNullString()
    {
        return null;
    }
}
