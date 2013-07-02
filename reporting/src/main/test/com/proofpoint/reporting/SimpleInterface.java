package com.proofpoint.reporting;

public interface SimpleInterface
{
    boolean isBooleanValue();

    void setBooleanValue(boolean booleanValue);

    Boolean isBooleanBoxedValue();

    void setBooleanBoxedValue(Boolean booleanBoxedValue);

    byte getByteValue();

    void setByteValue(byte byteValue);

    Byte getByteBoxedValue();

    void setByteBoxedValue(Byte byteBoxedValue);

    short getShortValue();

    void setShortValue(short shortValue);

    Short getShortBoxedValue();

    void setShortBoxedValue(Short shortBoxedValue);

    int getIntegerValue();

    void setIntegerValue(int integerValue);

    Integer getIntegerBoxedValue();

    void setIntegerBoxedValue(Integer integerBoxedValue);

    long getLongValue();

    void setLongValue(long longValue);

    Long getLongBoxedValue();

    void setLongBoxedValue(Long longBoxedValue);

    float getFloatValue();

    void setFloatValue(float floatValue);

    Float getFloatBoxedValue();

    void setFloatBoxedValue(Float floatBoxedValue);

    double getDoubleValue();

    void setDoubleValue(double doubleValue);

    Double getDoubleBoxedValue();

    void setDoubleBoxedValue(Double doubleBoxedValue);

    String getStringValue();

    void setStringValue(String stringValue);

    void setNotReported(int value);

    int getNotReported();

    Object getObjectValue();

    void setObjectValue(Object objectValue);
}
