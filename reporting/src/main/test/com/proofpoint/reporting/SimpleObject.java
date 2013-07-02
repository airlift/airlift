/*
 *  Copyright 2009 Martin Traverso
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

import com.proofpoint.stats.Reported;

public class SimpleObject
        implements SimpleInterface
{
    private boolean booleanValue;
    private Boolean booleanBoxedValue;
    private byte byteValue;
    private Byte byteBoxedValue;
    private short shortValue;
    private Short shortBoxedValue;
    private int integerValue;
    private Integer integerBoxedValue;
    private long longValue;
    private Long longBoxedValue;
    private float floatValue;
    private Float floatBoxedValue;
    private double doubleValue;
    private Double doubleBoxedValue;
    private String stringValue;
    private Object objectValue;
    private int privateValue;

    private int notReported;

    @Reported
    public boolean isBooleanValue()
    {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    @Reported
    public Boolean isBooleanBoxedValue()
    {
        return booleanBoxedValue;
    }

    public void setBooleanBoxedValue(Boolean booleanBoxedValue)
    {
        this.booleanBoxedValue = booleanBoxedValue;
    }

    @Reported
    public byte getByteValue()
    {
        return byteValue;
    }

    public void setByteValue(byte byteValue)
    {
        this.byteValue = byteValue;
    }

    @Reported
    public Byte getByteBoxedValue()
    {
        return byteBoxedValue;
    }

    public void setByteBoxedValue(Byte byteBoxedValue)
    {
        this.byteBoxedValue = byteBoxedValue;
    }

    @Reported
    public short getShortValue()
    {
        return shortValue;
    }

    public void setShortValue(short shortValue)
    {
        this.shortValue = shortValue;
    }

    @Reported
    public Short getShortBoxedValue()
    {
        return shortBoxedValue;
    }

    public void setShortBoxedValue(Short shortBoxedValue)
    {
        this.shortBoxedValue = shortBoxedValue;
    }

    @Reported
    public int getIntegerValue()
    {
        return integerValue;
    }

    public void setIntegerValue(int integerValue)
    {
        this.integerValue = integerValue;
    }

    @Reported
    public Integer getIntegerBoxedValue()
    {
        return integerBoxedValue;
    }

    public void setIntegerBoxedValue(Integer integerBoxedValue)
    {
        this.integerBoxedValue = integerBoxedValue;
    }

    @Reported
    public long getLongValue()
    {
        return longValue;
    }

    public void setLongValue(long longValue)
    {
        this.longValue = longValue;
    }

    @Reported
    public Long getLongBoxedValue()
    {
        return longBoxedValue;
    }

    public void setLongBoxedValue(Long longBoxedValue)
    {
        this.longBoxedValue = longBoxedValue;
    }

    @Reported
    public float getFloatValue()
    {
        return floatValue;
    }

    public void setFloatValue(float floatValue)
    {
        this.floatValue = floatValue;
    }

    @Reported
    public Float getFloatBoxedValue()
    {
        return floatBoxedValue;
    }

    public void setFloatBoxedValue(Float floatBoxedValue)
    {
        this.floatBoxedValue = floatBoxedValue;
    }

    @Reported
    public double getDoubleValue()
    {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @Reported
    public Double getDoubleBoxedValue()
    {
        return doubleBoxedValue;
    }

    public void setDoubleBoxedValue(Double doubleBoxedValue)
    {
        this.doubleBoxedValue = doubleBoxedValue;
    }

    @Reported
    public String getStringValue()
    {
        return stringValue;
    }

    @Reported
    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    public void setNotReported(int value)
    {
        this.notReported = value;
    }

    public int getNotReported()
    {
        return notReported;
    }

    @Reported
    public Object getObjectValue()
    {
        return objectValue;
    }

    public void setObjectValue(Object objectValue)
    {
        this.objectValue = objectValue;
    }

    @Reported
    private int getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(int privateValue)
    {
        this.privateValue = privateValue;
    }
}
