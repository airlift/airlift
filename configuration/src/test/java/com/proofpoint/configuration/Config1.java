package com.proofpoint.configuration;

public class Config1
{
    String stringOption;

    boolean booleanOption;

    Boolean boxedBooleanOption;

    byte byteOption;

    Byte boxedByteOption;

    short shortOption;

    Short boxedShortOption;

    int integerOption;

    Integer boxedIntegerOption;

    long longOption;

    Long boxedLongOption;

    float floatOption;

    Float boxedFloatOption;

    double doubleOption;

    Double boxedDoubleOption;

    MyEnum myEnumOption;

    ValueClass valueClassOption;

    public String getStringOption()
    {
        return stringOption;
    }

    @Config("stringOption")
    public void setStringOption(String stringOption)
    {
        this.stringOption = stringOption;
    }

    public boolean getBooleanOption()
    {
        return booleanOption;
    }

    @Config("booleanOption")
    public void setBooleanOption(boolean booleanOption)
    {
        this.booleanOption = booleanOption;
    }

    public Boolean getBoxedBooleanOption()
    {
        return boxedBooleanOption;
    }

    @Config("boxedBooleanOption")
    public void setBoxedBooleanOption(Boolean boxedBooleanOption)
    {
        this.boxedBooleanOption = boxedBooleanOption;
    }

    public byte getByteOption()
    {
        return byteOption;
    }

    @Config("byteOption")
    public void setByteOption(byte byteOption)
    {
        this.byteOption = byteOption;
    }

    public Byte getBoxedByteOption()
    {
        return boxedByteOption;
    }

    @Config("boxedByteOption")
    public void setBoxedByteOption(Byte boxedByteOption)
    {
        this.boxedByteOption = boxedByteOption;
    }

    public short getShortOption()
    {
        return shortOption;
    }

    @Config("shortOption")
    public void setShortOption(short shortOption)
    {
        this.shortOption = shortOption;
    }

    public Short getBoxedShortOption()
    {
        return boxedShortOption;
    }

    @Config("boxedShortOption")
    public void setBoxedShortOption(Short boxedShortOption)
    {
        this.boxedShortOption = boxedShortOption;
    }

    public int getIntegerOption()
    {
        return integerOption;
    }

    @Config("integerOption")
    public void setIntegerOption(int integerOption)
    {
        this.integerOption = integerOption;
    }

    public Integer getBoxedIntegerOption()
    {
        return boxedIntegerOption;
    }

    @Config("boxedIntegerOption")
    public void setBoxedIntegerOption(Integer boxedIntegerOption)
    {
        this.boxedIntegerOption = boxedIntegerOption;
    }

    public long getLongOption()
    {
        return longOption;
    }

    @Config("longOption")
    public void setLongOption(long longOption)
    {
        this.longOption = longOption;
    }

    public Long getBoxedLongOption()
    {
        return boxedLongOption;
    }

    @Config("boxedLongOption")
    public void setBoxedLongOption(Long boxedLongOption)
    {
        this.boxedLongOption = boxedLongOption;
    }

    public float getFloatOption()
    {
        return floatOption;
    }

    @Config("floatOption")
    public void setFloatOption(float floatOption)
    {
        this.floatOption = floatOption;
    }

    public Float getBoxedFloatOption()
    {
        return boxedFloatOption;
    }

    @Config("boxedFloatOption")
    public void setBoxedFloatOption(Float boxedFloatOption)
    {
        this.boxedFloatOption = boxedFloatOption;
    }

    public double getDoubleOption()
    {
        return doubleOption;
    }

    @Config("doubleOption")
    public void setDoubleOption(double doubleOption)
    {
        this.doubleOption = doubleOption;
    }

    public Double getBoxedDoubleOption()
    {
        return boxedDoubleOption;
    }

    @Config("boxedDoubleOption")
    public void setBoxedDoubleOption(Double boxedDoubleOption)
    {
        this.boxedDoubleOption = boxedDoubleOption;
    }

    public MyEnum getMyEnumOption()
    {
        return myEnumOption;
    }

    @Config("myEnumOption")
    public void setMyEnumOption(MyEnum myEnumOption)
    {
        this.myEnumOption = myEnumOption;
    }

    public ValueClass getValueClassOption()
    {
        return valueClassOption;
    }

    @Config("valueClassOption")
    public void setValueClassOption(ValueClass valueClassOption)
    {
        this.valueClassOption = valueClassOption;
    }
}