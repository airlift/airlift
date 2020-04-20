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
package io.airlift.jmx;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.airlift.discovery.client.ServiceAnnouncement;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.management.MBeanServer;

import java.lang.management.ManagementFactory;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class JmxModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(MBeanServer.class).toInstance(ManagementFactory.getPlatformMBeanServer());
        configBinder(binder).bindConfig(JmxConfig.class);

        newExporter(binder).export(StackTraceMBean.class).withGeneratedName();
        binder.bind(StackTraceMBean.class).in(Scopes.SINGLETON);

        JmxConfig jmxConfig = buildConfigObject(JmxConfig.class);

        if (jmxConfig.getRmiRegistryPort() == null) {
            // Do not export JmxAgent by default for security reasons.
            // Also, exporting it at randomly picked port was proven to be unreliable in certain environments.

            checkState(jmxConfig.getRmiServerPort() == null, "RMI registry port must be configured when RMI server port is configured");
        }
        else {
            discoveryBinder(binder).bindServiceAnnouncement(JmxAnnouncementProvider.class);

            if (JavaVersion.current().getMajor() < 9) {
                binder.bind(JmxAgent8.class).in(Scopes.SINGLETON);
                binder.bind(JmxAgent.class).to(JmxAgent8.class);
            }
            else {
                binder.bind(JmxAgent9.class).in(Scopes.SINGLETON);
                binder.bind(JmxAgent.class).to(JmxAgent9.class);
            }
        }
    }

    static class JmxAnnouncementProvider
            implements Provider<ServiceAnnouncement>
    {
        private JmxAgent jmxAgent;

        @Inject
        public void setJmxAgent(JmxAgent jmxAgent)
        {
            this.jmxAgent = jmxAgent;
        }

        @Override
        public ServiceAnnouncement get()
        {
            return serviceAnnouncement("jmx")
                    .addProperty("jmx", jmxAgent.getUrl().toString())
                    .build();
        }
    }
}
