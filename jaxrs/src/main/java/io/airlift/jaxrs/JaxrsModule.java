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
package io.airlift.jaxrs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.jaxrs.JsonParsingFeature.MappingEnabled.ENABLED;
import static org.glassfish.jersey.server.ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provides;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.jaxrs.JsonParsingFeature.MappingEnabled;
import io.airlift.jaxrs.tracing.JaxrsTracingModule;
import jakarta.servlet.Servlet;
import java.util.Set;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

public class JaxrsModule extends AbstractConfigurationAwareModule {
    public JaxrsModule() {}

    @Deprecated
    public JaxrsModule(boolean requireExplicitBindings) {
        checkArgument(requireExplicitBindings, "non-explicit bindings are no longer supported");
    }

    @Override
    protected void setup(Binder binder) {
        binder.disableCircularProxies();
        binder.bind(Servlet.class).to(Key.get(ServletContainer.class));
        newSetBinder(binder, Object.class, JaxrsResource.class).permitDuplicates();

        JaxrsBinder jaxrsBinder = jaxrsBinder(binder);
        jaxrsBinder.bind(JsonMapper.class);
        jaxrsBinder.bind(JsonParsingFeature.class);

        newOptionalBinder(binder, MappingEnabled.class).setDefault().toInstance(ENABLED);

        if (getProperty("tracing.enabled").map(Boolean::parseBoolean).orElse(false)) {
            install(new JaxrsTracingModule());
        }
    }

    @Provides
    public static ServletContainer createServletContainer(ResourceConfig resourceConfig) {
        return new ServletContainer(resourceConfig);
    }

    @Provides
    public static ResourceConfig createResourceConfig(@JaxrsResource Set<Object> jaxRsSingletons) {
        return new JaxrsResourceConfig(jaxRsSingletons)
                .setProperties(ImmutableMap.of(RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true"));
    }
}
