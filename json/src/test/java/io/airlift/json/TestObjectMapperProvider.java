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
package io.airlift.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.Objects;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestObjectMapperProvider
{
    @Test
    public void testAfterburnerIncluded()
            throws Exception
    {
        JsonCodecConfig config = new JsonCodecConfig()
                .setIncludeAfterBurnerModule(true);

        ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider(config);
        Set<Object> registeredModuleIds = objectMapperProvider.get().getRegisteredModuleIds();
        boolean afterBurnerModuleIncluded = false;
        for (Object registeredModuleId : registeredModuleIds) {
            if (registeredModuleId.equals("com.fasterxml.jackson.module.afterburner.AfterburnerModule")) {
                afterBurnerModuleIncluded = true;
            }
        }
        assertTrue(afterBurnerModuleIncluded);
    }

    @Test
    public void testAfterburnerCodec()
            throws Exception
    {
        JsonCodecConfig config = new JsonCodecConfig()
                .setIncludeAfterBurnerModule(true);

        ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider(config);
        DummyClass dummyClass = new DummyClass().setVal1("val1").setVal2(100);
        ObjectMapper objectMapper = objectMapperProvider.get();
        assertEquals(dummyClass, dummyClass);
        String json = objectMapper.writeValueAsString(dummyClass);
        DummyClass actual = objectMapper.readValue(json, DummyClass.class);
        assertEquals(actual, dummyClass);
    }

    public static class DummyClass
    {
        // These fields are public to make sure that Jackson is ignoring them
        public String val1;
        public int val2;

        @JsonProperty
        public String getVal1()
        {
            return val1;
        }

        @JsonProperty
        public DummyClass setVal1(String val1)
        {
            this.val1 = val1;
            return this;
        }

        @JsonProperty
        public int getVal2()
        {
            return val2;
        }

        @JsonProperty
        public DummyClass setVal2(int val2)
        {
            this.val2 = val2;
            return this;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DummyClass that = (DummyClass) o;
            return val2 == that.val2 &&
                    Objects.equals(val1, that.val1);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(val1, val2);
        }
    }
}
