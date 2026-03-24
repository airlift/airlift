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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;

public class JsonModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        // NOTE: this MUST NOT be a singleton because ObjectMappers are mutable. This means
        // one component could reconfigure the mapper and break all other components.
        // When updated to Jackson 3.x this is no longer a case since the JsonMapper instances
        // are immutable.
        binder.bind(JsonMapper.class).toProvider(JsonMapperProvider.class);
        bindDeprecatedProvider(binder);

        binder.bind(JsonCodecFactory.class).in(Scopes.SINGLETON);
    }

    @SuppressWarnings("deprecation")
    private static void bindDeprecatedProvider(Binder binder)
    {
        // NOTE: this MUST NOT be a singleton because ObjectMappers are mutable. This means
        // one component could reconfigure the mapper and break all other components.
        // When updated to Jackson 3.x this is no longer a case since the JsonMapper instances
        // are immutable.

        // For backward compatibility with the usage sites that depends on ObjectMapper
        binder.bind(ObjectMapper.class).toProvider(ObjectMapperProvider.class);
    }
}
