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
import com.google.inject.Module;

import java.util.function.Predicate;

import static io.airlift.configuration.ConfigurationAwareModule.combine;
import static java.util.Objects.requireNonNull;

public class ConditionalModule<T>
        extends AbstractConfigurationAwareModule
{
    /**
     * @deprecated Use {@link #conditionalModule} instead
     */
    @Deprecated
    public static <T> Module installModuleIf(Class<T> config, Predicate<T> predicate, Module module, Module otherwise)
    {
        return conditionalModule(config, predicate, module, otherwise);
    }

    /**
     * @deprecated Use {@link #conditionalModule} instead
     */
    @Deprecated
    public static <T> Module installModuleIf(Class<T> config, Predicate<T> predicate, Module module)
    {
        return conditionalModule(config, predicate, module);
    }

    public static <T> Module conditionalModule(Class<T> config, Predicate<T> predicate, Module module, Module otherwise)
    {
        return combine(
                conditionalModule(config, predicate, module),
                conditionalModule(config, predicate.negate(), otherwise));
    }

    public static <T> Module conditionalModule(Class<T> config, Predicate<T> predicate, Module module)
    {
        return new ConditionalModule<>(config, predicate, module);
    }

    private final Class<T> config;
    private final Predicate<T> predicate;
    private final Module module;

    private ConditionalModule(Class<T> config, Predicate<T> predicate, Module module)
    {
        this.config = requireNonNull(config, "config is null");
        this.predicate = requireNonNull(predicate, "predicate is null");
        this.module = requireNonNull(module, "module is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        T configuration = buildConfigObject(config);
        if (predicate.test(configuration)) {
            install(module);
        }
    }
}
