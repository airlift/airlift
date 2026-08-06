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
package io.airlift.json;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import static io.airlift.json.JsonBinder.jsonBinder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJsonBinder
{
    @Test
    public void testBindSerializer()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new JsonModule(),
                binder -> jsonBinder(binder).bindSerializer(new ToStringSerializer(Name.class)));

        JsonMapper jsonMapper = injector.getInstance(JsonMapper.class);
        assertThat(jsonMapper.writeValueAsString(new Name("dain"))).isEqualTo("\"dain\"");
    }

    @Test
    public void testBindSerializerRejectsObjectHandledType()
    {
        // ToStringSerializer.instance is declared as handling Object, which would install a
        // serializer for every type in the mapper
        assertThatThrownBy(() -> Guice.createInjector(binder -> jsonBinder(binder).bindSerializer(ToStringSerializer.instance)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("jsonSerializer.handledType can not be Object.class");
    }

    private record Name(String value)
    {
        @Override
        public String toString()
        {
            return value;
        }
    }
}
