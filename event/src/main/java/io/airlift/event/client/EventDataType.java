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
package io.airlift.event.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
enum EventDataType
{
    STRING(String.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, String.class);
                    jsonGenerator.writeString((String) value);
                }
            },

    BOOLEAN(Boolean.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Boolean.class);
                    jsonGenerator.writeBoolean((Boolean) value);
                }
            },

    BYTE(Byte.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Byte.class);
                    jsonGenerator.writeNumber((Byte) value);
                }
            },

    SHORT(Short.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Short.class);
                    jsonGenerator.writeNumber((Short) value);
                }
            },

    INTEGER(Integer.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Integer.class);
                    jsonGenerator.writeNumber((Integer) value);
                }
            },

    LONG(Long.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Long.class);
                    jsonGenerator.writeNumber((Long) value);
                }
            },

    FLOAT(Float.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Float.class);
                    jsonGenerator.writeNumber((Float) value);
                }
            },

    DOUBLE(Double.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Double.class);
                    jsonGenerator.writeNumber((Double) value);
                }
            },

    BIG_DECIMAL(BigDecimal.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, BigDecimal.class);
                    jsonGenerator.writeNumber((BigDecimal) value);
                }
            },

    BIG_INTEGER(BigInteger.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, BigInteger.class);
                    jsonGenerator.writeNumber(new BigDecimal((BigInteger) value));
                }
            },

    DATETIME(DateTime.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, DateTime.class);
                    jsonGenerator.writeString(ISO_DATETIME_FORMAT.print((DateTime) value));
                }
            },

    ENUM(Enum.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, Enum.class);
                    jsonGenerator.writeString(value.toString());
                }
            },

    INET_ADDRESS(InetAddress.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, InetAddress.class);
                    jsonGenerator.writeString(((InetAddress) value).getHostAddress());
                }
            },

    UUID(java.util.UUID.class)
            {
                public void writeFieldValue(JsonGenerator jsonGenerator, Object value)
                        throws IOException
                {
                    validateFieldValueType(value, java.util.UUID.class);
                    jsonGenerator.writeString(value.toString());
                }
            };

    private static final DateTimeFormatter ISO_DATETIME_FORMAT = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC);

    private static final Map<Class<?>, EventDataType> byType;

    static {
        ImmutableMap.Builder<Class<?>, EventDataType> builder = ImmutableMap.builder();
        for (EventDataType eventDataType : EventDataType.values()) {
            Class<?> dataType = eventDataType.getType();
            builder.put(dataType, eventDataType);
            if (Primitives.isWrapperType(dataType)) {
                builder.put(Primitives.unwrap(dataType), eventDataType);
            }
        }
        byType = builder.build();
    }

    public static EventDataType getEventDataType(Class<?> type)
    {
        return byType.get(type);
    }

    private final Class<?> type;

    EventDataType(Class<?> type)
    {
        this.type = type;
    }

    public Class<?> getType()
    {
        return type;
    }

    static void validateFieldValueType(Object value, Class<?> expectedType)
    {
        Preconditions.checkNotNull(value, "value is null");
        Preconditions.checkArgument(expectedType.isInstance(value),
                "Expected 'value' to be a " + expectedType.getSimpleName() +
                        " but it is a " + value.getClass().getName());
    }

    public abstract void writeFieldValue(JsonGenerator jsonGenerator, Object value)
            throws IOException;
}
