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
package io.airlift.yaml;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.airlift.yaml.YamlBinder.yamlBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestYamlModule
{
    @Test
    public void testYamlCodecFactoryBinding()
    {
        Injector injector = Guice.createInjector(new YamlModule());
        YamlCodecFactory codecFactory = injector.getInstance(YamlCodecFactory.class);

        Person expected = new Person().setName("dain").setRocks(true);
        expected.setLastName(Optional.of("Awesome"));

        YamlCodec<Person> codec = codecFactory.yamlCodec(Person.class);
        assertThat(codec.fromYaml(codec.toYaml(expected))).isEqualTo(expected);

        YamlCodec<List<Person>> listCodec = codecFactory.listYamlCodec(Person.class);
        assertThat(listCodec.fromYaml(listCodec.toYaml(List.of(expected)))).containsExactly(expected);
    }

    @Test
    public void testYamlBinderSerializerAppliesToYamlMapper()
    {
        // YamlBinder has its own multibinders (independent of JsonBinder). A serializer registered
        // through YamlBinder is applied to the YAML mapper.
        Injector injector = Guice.createInjector(
                new YamlModule(),
                binder -> yamlBinder(binder)
                        .addSerializerBinding(BoxedName.class)
                        .toInstance(ToStringSerializer.instance));

        YAMLMapper mapper = injector.getInstance(YAMLMapper.class);
        BoxedName expected = new BoxedName("dain");

        try {
            String yaml = mapper.writeValueAsString(expected);
            assertThat(yaml).contains("dain");
        }
        catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static class BoxedName
    {
        private final String name;

        public BoxedName(String name)
        {
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }
}
