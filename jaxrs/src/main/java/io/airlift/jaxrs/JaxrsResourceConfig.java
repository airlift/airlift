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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Set;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.Resource;

public class JaxrsResourceConfig extends ResourceConfig {
    public JaxrsResourceConfig(Set<Object> singletons) {
        ImmutableMap.Builder<Class<?>, Object> builder = ImmutableMap.builder();
        for (Object singleton : singletons) {
            Class<?> clazz = singleton.getClass();
            if (singleton instanceof Class<?> clazzInstance) {
                register(clazzInstance);
            } else if (Providers.isProvider(clazz) || Binder.class.isAssignableFrom(clazz)) {
                // If Jersey supports this component's class (including Binders), register directly, so we can get
                // @Context injections
                register(singleton);
            } else if (singleton instanceof Resource resource) {
                registerResources(resource);
            } else {
                builder.put(clazz, singleton);
            }
        }

        Map<Class<?>, Object> classToInstance = builder.buildOrThrow();
        registerClasses(classToInstance.keySet());
        register(new SingletonsBinderBridge(classToInstance));
    }

    // Allows HK2 to retrieve instances of registered singleton resources that we got from Guice
    private static class SingletonsBinderBridge extends AbstractBinder {
        private final Map<Class<?>, Object> singletons;

        public SingletonsBinderBridge(Map<Class<?>, Object> singletons) {
            this.singletons = ImmutableMap.copyOf(singletons);
        }

        @Override
        public void configure() {
            for (Map.Entry<Class<?>, Object> singleton : singletons.entrySet()) {
                bind(singleton.getValue()).to(singleton.getKey());
            }
        }
    }
}
