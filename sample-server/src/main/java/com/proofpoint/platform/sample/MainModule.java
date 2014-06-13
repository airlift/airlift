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

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.event.client.EventBinder.eventBinder;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.reporting.ReportBinder.reportBinder;

public class MainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(PersonStore.class).in(Scopes.SINGLETON);
        reportBinder(binder).export(PersonStore.class).withGeneratedName();

        jaxrsBinder(binder).bind(PersonsResource.class);
        jaxrsBinder(binder).bind(PersonResource.class);

        bindConfig(binder).to(StoreConfig.class);
        eventBinder(binder).bindEventClient(PersonEvent.class);

        discoveryBinder(binder).bindHttpAnnouncement("person");
    }
}
