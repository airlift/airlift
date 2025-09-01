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

import com.google.inject.Binder;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.jaxrs.JsonParsingFeature.MappingEnabled;
import io.airlift.jaxrs.tracing.TracingDynamicFeature;
import jakarta.annotation.Nullable;
import jakarta.servlet.Servlet;
import org.glassfish.jersey.server.ResourceConfig;

import java.lang.annotation.Annotation;
import java.util.Optional;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static io.airlift.jaxrs.BinderUtils.qualifiedKey;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static io.airlift.jaxrs.JsonParsingFeature.MappingEnabled.ENABLED;

public class JaxrsModule
        extends AbstractConfigurationAwareModule
{
    private final Optional<Class<? extends Annotation>> qualifier;

    public JaxrsModule()
    {
        this(null);
    }

    public JaxrsModule(@Nullable Class<? extends Annotation> qualifier)
    {
        this.qualifier = Optional.ofNullable(qualifier);
    }

    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();
        binder.bind(qualifiedKey(qualifier, Servlet.class))
                .toProvider(new JaxrsServletProvider(qualifier));
        binder.bind(qualifiedKey(qualifier, ResourceConfig.class))
                .toProvider(new JaxrsResourceConfigProvider(qualifier));
        newSetBinder(binder, qualifiedKey(qualifier, Object.class)).permitDuplicates();

        JaxrsBinder jaxrsBinder = jaxrsBinder(binder, qualifier);
        jaxrsBinder.bind(JsonMapper.class);
        jaxrsBinder.bind(JsonParsingFeature.class, new JsonParsingFeature.Provider(qualifier));

        newOptionalBinder(binder, qualifiedKey(qualifier, MappingEnabled.class))
                .setDefault()
                .toInstance(ENABLED);

        if (getProperty("tracing.enabled").map(Boolean::parseBoolean).orElse(false)) {
            jaxrsBinder.bind(TracingDynamicFeature.class);
        }
    }
}
