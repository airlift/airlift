package com.proofpoint.configuration;

interface Config1
{
    @Config("stringOption")
    String getStringOption();

    @Config("booleanOption")
    boolean getBooleanOption();

    @Config("boxedBooleanOption")
    Boolean getBoxedBooleanOption();

    @Config("byteOption")
    byte getByteOption();

    @Config("boxedByteOption")
    Byte getBoxedByteOption();

    @Config("shortOption")
    short getShortOption();

    @Config("boxedShortOption")
    Short getBoxedShortOption();

    @Config("integerOption")
    int getIntegerOption();

    @Config("boxedIntegerOption")
    Integer getBoxedIntegerOption();

    @Config("longOption")
    long getLongOption();

    @Config("boxedLongOption")
    Long getBoxedLongOption();

    @Config("floatOption")
    float getFloatOption();

    @Config("boxedFloatOption")
    Float getBoxedFloatOption();

    @Config("doubleOption")
    double getDoubleOption();

    @Config("boxedDoubleOption")
    Double getBoxedDoubleOption();
}