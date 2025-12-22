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
package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public interface ConfigurationAwareModule
        extends Module
{
    void setConfigurationFactory(ConfigurationFactory configurationFactory);

    static Module combine(Module... modules)
    {
        return combine(ImmutableList.copyOf(modules));
    }

    static Module combine(Iterable<Module> modulesIterable)
    {
        List<Module> modules = ImmutableList.copyOf(modulesIterable);
        checkArgument(!modules.isEmpty(), "no modules provided");
        if (modules.size() == 1) {
            return modules.getFirst();
        }
        return new AbstractConfigurationAwareModule()
        {
            @Override
            protected void setup(Binder binder)
            {
                for (Module module : modules) {
                    install(module);
                }
            }
        };
    }
}
