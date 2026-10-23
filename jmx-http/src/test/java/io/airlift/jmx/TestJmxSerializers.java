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
package io.airlift.jmx;

import io.airlift.jmx.JmxHttpModule.CompositeDataSerializer;
import io.airlift.jmx.JmxHttpModule.TabularDataSerializer;
import io.airlift.json.JsonMapperProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import static org.assertj.core.api.Assertions.assertThat;

public class TestJmxSerializers
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get()
            .rebuild()
            .addModule(new SimpleModule()
                    .addSerializer(TabularData.class, new TabularDataSerializer())
                    .addSerializer(CompositeData.class, new CompositeDataSerializer()))
            .build();

    @Test
    public void testCompositeData()
            throws Exception
    {
        assertThat(JSON_MAPPER.writeValueAsString(entry("a", 1L))).isEqualTo("{\"key\":\"a\",\"value\":1}");
    }

    @Test
    public void testTabularData()
            throws Exception
    {
        TabularData data = new TabularDataSupport(new TabularType("table", "table", entryType(), new String[] {"key"}));
        data.put(entry("a", 1L));
        data.put(entry("b", 2L));

        assertThat(JSON_MAPPER.readTree(JSON_MAPPER.writeValueAsString(data)))
                .isEqualTo(JSON_MAPPER.readTree("[{\"key\":\"a\",\"value\":1},{\"key\":\"b\",\"value\":2}]"));
    }

    private static CompositeData entry(String key, long value)
            throws Exception
    {
        return new CompositeDataSupport(entryType(), new String[] {"key", "value"}, new Object[] {key, value});
    }

    private static CompositeType entryType()
            throws Exception
    {
        return new CompositeType("entry", "entry", new String[] {"key", "value"}, new String[] {"key", "value"}, new OpenType<?>[] {SimpleType.STRING, SimpleType.LONG});
    }
}
