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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.airlift.yaml.YamlCodec.listYamlCodec;
import static io.airlift.yaml.YamlCodecBinder.yamlCodecBinder;
import static org.assertj.core.api.Assertions.assertThat;

public class TestYamlCodecBinder
{
    @Test
    public void ignoresRepeatedBinding()
    {
        Injector injector = Guice.createInjector(
                new YamlModule(),
                binder -> {
                    yamlCodecBinder(binder).bindYamlCodec(Integer.class);
                    yamlCodecBinder(binder).bindYamlCodec(Integer.class);

                    binder.bind(Dummy.class).in(Scopes.SINGLETON);
                });

        assertThat(injector.getInstance(Dummy.class).getCodec()).isNotNull();
    }

    @Test
    public void testMapListYamlCodec()
    {
        Injector injector = Guice.createInjector(
                new YamlModule(),
                binder -> yamlCodecBinder(binder).bindMapYamlCodec(String.class, listYamlCodec(Dummy.class)));

        assertThat(injector.getInstance(Key.get(new TypeLiteral<YamlCodec<Map<String, List<Dummy>>>>() {})))
                .isNotNull();
    }

    private static class Dummy
    {
        private final YamlCodec<Integer> codec;

        @Inject
        public Dummy(YamlCodec<Integer> codec)
        {
            this.codec = codec;
        }

        public YamlCodec<Integer> getCodec()
        {
            return codec;
        }
    }
}
