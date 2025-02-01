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

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class SwitchModule<T>
        extends AbstractConfigurationAwareModule
{
    public static <C, V> Module switchModule(
            Class<C> config,
            Function<C, V> valueProvider,
            Function<V, Module> moduleProvider)
    {
        requireNonNull(valueProvider, "valueProvider is null");
        requireNonNull(moduleProvider, "moduleProvider is null");
        return new SwitchModule<>(config, valueProvider.andThen(moduleProvider));
    }

    private final Class<T> config;
    private final Function<T, Module> moduleProvider;

    private SwitchModule(Class<T> config, Function<T, Module> moduleProvider)
    {
        this.config = requireNonNull(config, "config is null");
        this.moduleProvider = requireNonNull(moduleProvider, "moduleProvider is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        T configuration = buildConfigObject(config);
        install(moduleProvider.apply(configuration));
    }
}
