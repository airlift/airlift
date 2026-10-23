/*
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
package io.airlift.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ext.javatime.ser.YearSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.Serializers;
import tools.jackson.databind.ser.jdk.EnumSerializer;

import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;

/**
 * Serializes {@link Month} and {@link Year} the way Jackson 2.x did. Jackson 3.x always writes
 * them as numbers; 2.x wrote a {@code Month} as an enum (its name) and a {@code Year} as a string
 * unless timestamps were enabled. {@code @JsonFormat} on a property is honored as before.
 */
class LegacyJavaTimeModule
        extends SimpleModule
{
    LegacyJavaTimeModule()
    {
        super("airlift-legacy-java-time", Version.unknownVersion());
    }

    @Override
    public void setupModule(SetupContext context)
    {
        super.setupModule(context);
        context.addSerializers(new Serializers.Base()
        {
            @Override
            public ValueSerializer<?> findEnumSerializer(SerializationConfig config, JavaType type, BeanDescription.Supplier beanDescription, JsonFormat.Value formatOverrides)
            {
                if (type.getRawClass() == Month.class) {
                    return EnumSerializer.construct(Month.class, config, beanDescription.get(), formatOverrides);
                }
                return null;
            }

            @Override
            public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type, BeanDescription.Supplier beanDescription, JsonFormat.Value formatOverrides)
            {
                if (type.getRawClass() == Year.class) {
                    return new LegacyYearSerializer();
                }
                return null;
            }
        });
    }

    private static class LegacyYearSerializer
            extends YearSerializer
    {
        public LegacyYearSerializer()
        {
            super(null);
        }

        private LegacyYearSerializer(LegacyYearSerializer base, DateTimeFormatter formatter, Boolean useTimestamp)
        {
            super(base, formatter, useTimestamp);
        }

        @Override
        protected YearSerializer withFormat(DateTimeFormatter formatter, Boolean useTimestamp, JsonFormat.Shape shape)
        {
            return new LegacyYearSerializer(this, formatter, useTimestamp);
        }

        @Override
        protected boolean useTimestampFromGlobalDefaults(SerializationContext context)
        {
            // Jackson 3.x always writes a Year as a number by default; 2.x followed WRITE_DATES_AS_TIMESTAMPS
            return (context != null) && context.isEnabled(getTimestampsFeature());
        }
    }
}
