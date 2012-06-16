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
package io.airlift.configuration;

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
    public Config1 setStringOption(String stringOption)
    {
        this.stringOption = stringOption;
        return this;
    }

    public boolean getBooleanOption()
    {
        return booleanOption;
    }

    @Config("booleanOption")
    public Config1 setBooleanOption(boolean booleanOption)
    {
        this.booleanOption = booleanOption;
        return this;
    }

    public Boolean getBoxedBooleanOption()
    {
        return boxedBooleanOption;
    }

    @Config("boxedBooleanOption")
    public Config1 setBoxedBooleanOption(Boolean boxedBooleanOption)
    {
        this.boxedBooleanOption = boxedBooleanOption;
        return this;
    }

    public byte getByteOption()
    {
        return byteOption;
    }

    @Config("byteOption")
    public Config1 setByteOption(byte byteOption)
    {
        this.byteOption = byteOption;
        return this;
    }

    public Byte getBoxedByteOption()
    {
        return boxedByteOption;
    }

    @Config("boxedByteOption")
    public Config1 setBoxedByteOption(Byte boxedByteOption)
    {
        this.boxedByteOption = boxedByteOption;
        return this;
    }

    public short getShortOption()
    {
        return shortOption;
    }

    @Config("shortOption")
    public Config1 setShortOption(short shortOption)
    {
        this.shortOption = shortOption;
        return this;
    }

    public Short getBoxedShortOption()
    {
        return boxedShortOption;
    }

    @Config("boxedShortOption")
    public Config1 setBoxedShortOption(Short boxedShortOption)
    {
        this.boxedShortOption = boxedShortOption;
        return this;
    }

    public int getIntegerOption()
    {
        return integerOption;
    }

    @Config("integerOption")
    public Config1 setIntegerOption(int integerOption)
    {
        this.integerOption = integerOption;
        return this;
    }

    public Integer getBoxedIntegerOption()
    {
        return boxedIntegerOption;
    }

    @Config("boxedIntegerOption")
    public Config1 setBoxedIntegerOption(Integer boxedIntegerOption)
    {
        this.boxedIntegerOption = boxedIntegerOption;
        return this;
    }

    public long getLongOption()
    {
        return longOption;
    }

    @Config("longOption")
    public Config1 setLongOption(long longOption)
    {
        this.longOption = longOption;
        return this;
    }

    public Long getBoxedLongOption()
    {
        return boxedLongOption;
    }

    @Config("boxedLongOption")
    public Config1 setBoxedLongOption(Long boxedLongOption)
    {
        this.boxedLongOption = boxedLongOption;
        return this;
    }

    public float getFloatOption()
    {
        return floatOption;
    }

    @Config("floatOption")
    public Config1 setFloatOption(float floatOption)
    {
        this.floatOption = floatOption;
        return this;
    }

    public Float getBoxedFloatOption()
    {
        return boxedFloatOption;
    }

    @Config("boxedFloatOption")
    public Config1 setBoxedFloatOption(Float boxedFloatOption)
    {
        this.boxedFloatOption = boxedFloatOption;
        return this;
    }

    public double getDoubleOption()
    {
        return doubleOption;
    }

    @Config("doubleOption")
    public Config1 setDoubleOption(double doubleOption)
    {
        this.doubleOption = doubleOption;
        return this;
    }

    public Double getBoxedDoubleOption()
    {
        return boxedDoubleOption;
    }

    @Config("boxedDoubleOption")
    public Config1 setBoxedDoubleOption(Double boxedDoubleOption)
    {
        this.boxedDoubleOption = boxedDoubleOption;
        return this;
    }

    public MyEnum getMyEnumOption()
    {
        return myEnumOption;
    }

    @Config("myEnumOption")
    public Config1 setMyEnumOption(MyEnum myEnumOption)
    {
        this.myEnumOption = myEnumOption;
        return this;
    }

    public ValueClass getValueClassOption()
    {
        return valueClassOption;
    }

    @Config("valueClassOption")
    public Config1 setValueClassOption(ValueClass valueClassOption)
    {
        this.valueClassOption = valueClassOption;
        return this;
    }
}