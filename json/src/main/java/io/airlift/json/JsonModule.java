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
package io.airlift.json;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class).in(Scopes.SINGLETON);
        binder.bind(JsonMapper.class).toProvider(ObjectMapperProvider.class).in(Scopes.SINGLETON);

        binder.bind(JsonCodecFactory.class).in(Scopes.SINGLETON);
    }
}
