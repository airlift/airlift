package com.proofpoint.reporting;

import com.proofpoint.stats.Bucketed;
import com.proofpoint.stats.ReportedAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class CustomAnnotationObject
        extends SimpleBucketed
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
    private int privateValue;

    private int notReported;

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    @ReportedAnnotation
    public @interface Reported1
    {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    @ReportedAnnotation
    public @interface Reported2
    {
    }

    @Reported1
    public boolean isBooleanValue()
    {
        return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    @Reported2
    public Boolean isBooleanBoxedValue()
    {
        return booleanBoxedValue;
    }

    public void setBooleanBoxedValue(Boolean booleanBoxedValue)
    {
        this.booleanBoxedValue = booleanBoxedValue;
    }

    @Reported1
    public byte getByteValue()
    {
        return byteValue;
    }

    public void setByteValue(byte byteValue)
    {
        this.byteValue = byteValue;
    }

    @Reported2
    public Byte getByteBoxedValue()
    {
        return byteBoxedValue;
    }

    public void setByteBoxedValue(Byte byteBoxedValue)
    {
        this.byteBoxedValue = byteBoxedValue;
    }

    @Reported2
    public short getShortValue()
    {
        return shortValue;
    }

    public void setShortValue(short shortValue)
    {
        this.shortValue = shortValue;
    }

    @Reported1
    public Short getShortBoxedValue()
    {
        return shortBoxedValue;
    }

    public void setShortBoxedValue(Short shortBoxedValue)
    {
        this.shortBoxedValue = shortBoxedValue;
    }

    @Reported1
    public int getIntegerValue()
    {
        return integerValue;
    }

    public void setIntegerValue(int integerValue)
    {
        this.integerValue = integerValue;
    }

    @Reported2
    public Integer getIntegerBoxedValue()
    {
        return integerBoxedValue;
    }

    public void setIntegerBoxedValue(Integer integerBoxedValue)
    {
        this.integerBoxedValue = integerBoxedValue;
    }

    @Reported1
    public long getLongValue()
    {
        return longValue;
    }

    public void setLongValue(long longValue)
    {
        this.longValue = longValue;
    }

    @Reported1
    public Long getLongBoxedValue()
    {
        return longBoxedValue;
    }

    public void setLongBoxedValue(Long longBoxedValue)
    {
        this.longBoxedValue = longBoxedValue;
    }

    @Reported2
    public float getFloatValue()
    {
        return floatValue;
    }

    public void setFloatValue(float floatValue)
    {
        this.floatValue = floatValue;
    }

    @Reported1
    public Float getFloatBoxedValue()
    {
        return floatBoxedValue;
    }

    public void setFloatBoxedValue(Float floatBoxedValue)
    {
        this.floatBoxedValue = floatBoxedValue;
    }

    @Reported1
    public double getDoubleValue()
    {
        return this.doubleValue;
    }

    public void setDoubleValue(double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @Reported1
    public Double getDoubleBoxedValue()
    {
        return doubleBoxedValue;
    }

    public void setDoubleBoxedValue(Double doubleBoxedValue)
    {
        this.doubleBoxedValue = doubleBoxedValue;
    }

    public void setNotReported(int value)
    {
        this.notReported = value;
    }

    public int getNotReported()
    {
        return notReported;
    }

    @Reported1
    private int getPrivateValue()
    {
        return privateValue;
    }

    private void setPrivateValue(int privateValue)
    {
        this.privateValue = privateValue;
    }
}
