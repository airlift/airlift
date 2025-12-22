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
package io.airlift.configuration;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;

import static io.airlift.configuration.ConfigurationAwareModule.combine;
import static java.util.Objects.requireNonNull;

/**
 * ConditionalModule is not preferable to using buildConfigObject directly.
 * For example,
 * <pre>
 * install(conditionalModule(
 *         SomeConfig.class,
 *         config -> config.getSomeOption().equals("x"),
 *         binder -> binder.bind(String.class).toInstance("X")));
 * </pre>
 *
 * can be replaced with:
 * <pre>
 * SomeConfig testConfig = buildConfigObject(SomeConfig.class);
 * if (testConfig.getSomeOption().equals("x")) {
 *         binder.bind(String.class).toInstance("X");
 * }
 * </pre>
 */
public class ConditionalModule<T>
        extends AbstractConfigurationAwareModule
{
    @Deprecated
    public static <T> Module conditionalModule(Class<T> config, Predicate<T> predicate, Module module, Module otherwise)
    {
        return combine(
                conditionalModule(config, predicate, module),
                conditionalModule(config, predicate.negate(), otherwise));
    }

    @Deprecated
    public static <T> Module conditionalModule(Class<T> config, String prefix, Predicate<T> predicate, Module module, Module otherwise)
    {
        return combine(
                conditionalModule(config, prefix, predicate, module),
                conditionalModule(config, prefix, predicate.negate(), otherwise));
    }

    @Deprecated
    public static <T> Module conditionalModule(Class<T> config, Predicate<T> predicate, Module module)
    {
        return conditionalModule(config, null, predicate, module);
    }

    @Deprecated
    public static <T> Module conditionalModule(Class<T> config, String prefix, Predicate<T> predicate, Module module)
    {
        return new ConditionalModule<>(Key.get(config), config, prefix, predicate, module);
    }

    @Deprecated
    public static <T> Module conditionalModule(Key<T> key, Class<T> config, String prefix, Predicate<T> predicate, Module module)
    {
        return new ConditionalModule<>(key, config, prefix, predicate, module);
    }

    private final Key<T> key;
    private final Class<T> config;
    private final @Nullable String prefix;
    private final Predicate<T> predicate;
    private final Module module;

    private ConditionalModule(Key<T> key, Class<T> config, String prefix, Predicate<T> predicate, Module module)
    {
        this.key = requireNonNull(key, "key is null");
        this.config = requireNonNull(config, "config is null");
        this.prefix = prefix;
        this.predicate = requireNonNull(predicate, "predicate is null");
        this.module = requireNonNull(module, "module is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        if (predicate.test(buildConfigObject(key, config, prefix))) {
            install(module);
        }
    }
}
