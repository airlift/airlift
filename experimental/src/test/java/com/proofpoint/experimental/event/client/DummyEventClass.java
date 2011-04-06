package com.proofpoint.experimental.event.client;

@EventType("test:type=version1,name=dummy")
public class DummyEventClass
{
    private final double doubleValue;
    private final int intValue;
    private final String stringValue;
    private final boolean boolValue;

    public DummyEventClass(double doubleValue, int intValue, String stringValue, boolean boolValue)
    {
        this.doubleValue = doubleValue;
        this.intValue = intValue;
        this.stringValue = stringValue;
        this.boolValue = boolValue;
    }

    @EventField
    public double getDoubleValue()
    {
        return doubleValue;
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
    public boolean isBoolValue()
    {
        return boolValue;
    }
}
