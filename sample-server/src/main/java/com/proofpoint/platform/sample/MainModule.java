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
package com.proofpoint.platform.sample;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.experimental.discovery.client.DiscoveryBinder;
import org.weakref.jmx.guice.MBeanModule;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.experimental.event.client.EventBinder.eventBinder;

public class MainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(PersonStore.class).in(Scopes.SINGLETON);
        MBeanModule.newExporter(binder).export(PersonStore.class).withGeneratedName();

        binder.bind(PersonsResource.class).in(Scopes.SINGLETON);
        binder.bind(PersonResource.class).in(Scopes.SINGLETON);

        bindConfig(binder).to(StoreConfig.class);
        eventBinder(binder).bindEventClient(PersonEvent.class);

        DiscoveryBinder.discoveryBinder(binder).bindHttpAnnouncement("person");
    }
}
