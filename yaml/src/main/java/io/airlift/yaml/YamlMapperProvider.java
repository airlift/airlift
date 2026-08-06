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

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import io.airlift.jackson.BaseJacksonProvider;
import org.yaml.snakeyaml.LoaderOptions;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public class YamlMapperProvider
        extends BaseJacksonProvider<YAMLMapper, YAMLMapper.Builder, YamlMapperProvider>
{
    public YamlMapperProvider()
    {
        this(yamlFactoryBuilder());
    }

    private YamlMapperProvider(YAMLFactoryBuilder yamlFactoryBuilder)
    {
        super(yamlFactoryBuilder, YAMLMapper::builder);

        // YAML defaults tuned for human-readable output
        Map<YAMLGenerator.Feature, Boolean> defaults = new EnumMap<>(YAMLGenerator.Feature.class);
        // Emit unquoted strings where YAML grammar allows
        defaults.put(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);
        // Use `|` for multi-line strings
        defaults.put(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true);
        // Do not wrap long lines
        defaults.put(YAMLGenerator.Feature.SPLIT_LINES, false);
        // Indent nested arrays for readability
        defaults.put(YAMLGenerator.Feature.INDENT_ARRAYS, true);
        defaults.put(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true);
        // Do not emit `---` at the top of documents
        defaults.put(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        // SnakeYAML truncates keys over 128 chars by default
        defaults.put(YAMLGenerator.Feature.ALLOW_LONG_KEYS, true);

        defaults.forEach((feature, enabled) -> {
            if (enabled) {
                mapperBuilder().enable(feature);
            }
            else {
                mapperBuilder().disable(feature);
            }
        });
    }

    public YamlMapperProvider withGeneratorFeature(YAMLGenerator.Feature feature, boolean enabled)
    {
        requireNonNull(feature, "feature is null");
        if (enabled) {
            mapperBuilder().enable(feature);
        }
        else {
            mapperBuilder().disable(feature);
        }
        return this;
    }

    // Redirect the injected serde/module bindings to the YAML namespace so YAML and JSON stay
    // decoupled. Polymorphic subtypes are intentionally left inheriting the shared (unannotated)
    // Set<JacksonSubType> binding, since they describe Java types rather than a wire format.

    @Override
    @Inject(optional = true)
    public void setJsonSerializers(@Yaml Map<Class<?>, JsonSerializer<?>> jsonSerializers)
    {
        super.setJsonSerializers(jsonSerializers);
    }

    @Override
    @Inject(optional = true)
    public void setJsonDeserializers(@Yaml Map<Class<?>, JsonDeserializer<?>> jsonDeserializers)
    {
        super.setJsonDeserializers(jsonDeserializers);
    }

    @Override
    @Inject(optional = true)
    public void setKeySerializers(@YamlKeySerde Map<Class<?>, JsonSerializer<?>> keySerializers)
    {
        super.setKeySerializers(keySerializers);
    }

    @Override
    @Inject(optional = true)
    public void setKeyDeserializers(@YamlKeySerde Map<Class<?>, KeyDeserializer> keyDeserializers)
    {
        super.setKeyDeserializers(keyDeserializers);
    }

    @Override
    @Inject(optional = true)
    public void setModules(@Yaml Set<Module> modules)
    {
        super.setModules(modules);
    }

    @Override
    public YAMLMapper get()
    {
        return create();
    }

    private static YAMLFactoryBuilder yamlFactoryBuilder()
    {
        // SnakeYAML enforces its own limits independent of Jackson's StreamReadConstraints.
        // Lift the codepoint limit so callers control input length (consistent with the JSON
        // base provider) and disallow duplicate keys for correctness.
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(Integer.MAX_VALUE);
        loaderOptions.setAllowDuplicateKeys(false);

        YAMLFactoryBuilder builder = YAMLFactory.builder();
        builder.loaderOptions(loaderOptions);
        return builder;
    }
}
