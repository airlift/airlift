/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.bootstrap;

import com.google.common.base.Function;
import com.google.inject.Binder;
import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import com.proofpoint.node.ApplicationNameModule;

import static com.google.common.base.Preconditions.checkNotNull;

class DynamicApplicationNameModule<T> extends AbstractConfigurationAwareModule
{
    private final Class<T> configClass;
    private final Function<T, String> applicationNameFunction;

    DynamicApplicationNameModule(Class<T> configClass, Function<T, String> applicationNameFunction)
    {
        this.configClass = checkNotNull(configClass, "configClass is null");
        this.applicationNameFunction = checkNotNull(applicationNameFunction, "applicationNameFunction is null");
    }

    @Override
    protected void setup(Binder binder)
    {
        T configObject = buildConfigObject(configClass);
        binder.install(new ApplicationNameModule(applicationNameFunction.apply(configObject)));
    }
}
