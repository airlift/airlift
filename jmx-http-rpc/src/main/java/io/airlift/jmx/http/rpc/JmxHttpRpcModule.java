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
package io.airlift.jmx.http.rpc;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import io.airlift.discovery.client.ServiceAnnouncement;
import io.airlift.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.TheAdminServlet;
import io.airlift.node.NodeInfo;

import javax.servlet.Servlet;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Map;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.discovery.client.DiscoveryBinder.discoveryBinder;
import static io.airlift.discovery.client.ServiceAnnouncement.serviceAnnouncement;

@Beta
public class JmxHttpRpcModule implements Module
{
    private final Class<? extends Annotation> bindingAnnotation;

    public JmxHttpRpcModule()
    {
        this(TheAdminServlet.class);
    }

    public JmxHttpRpcModule(Class<? extends Annotation> bindingAnnotation)
    {
        this.bindingAnnotation = bindingAnnotation;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(bindingAnnotation).to(MBeanServerServlet.class).in(Scopes.SINGLETON);
        binder.bind(new TypeLiteral<Map<String, String>>() {}).annotatedWith(bindingAnnotation).toInstance(ImmutableMap.<String, String>of());

        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement("jmx-http-rpc");
        discoveryBinder(binder).bindServiceAnnouncement(new JmxHttpRpcAnnouncementProvider(serviceAnnouncementBuilder));

        configBinder(binder).bindConfig(JmxHttpRpcConfig.class);
    }

    @Provides
    public HttpMBeanServerCredentials createCredentials(JmxHttpRpcConfig config)
    {
        return new HttpMBeanServerCredentials(config.getUsername(), config.getPassword());
    }


    static class JmxHttpRpcAnnouncementProvider implements Provider<ServiceAnnouncement>
    {
        private final ServiceAnnouncementBuilder builder;
        private HttpServerInfo httpServerInfo;
        private NodeInfo nodeInfo;

        public JmxHttpRpcAnnouncementProvider(ServiceAnnouncementBuilder serviceAnnouncementBuilder)
        {
            builder = serviceAnnouncementBuilder;
        }

        @Inject
        public synchronized void setHttpServerInfo(HttpServerInfo httpServerInfo)
        {
            this.httpServerInfo = httpServerInfo;
        }

        @Inject
        public synchronized void setNodeInfo(NodeInfo nodeInfo)
        {
            this.nodeInfo = nodeInfo;
        }

        @Override
        public synchronized ServiceAnnouncement get()
        {
            if (httpServerInfo.getAdminUri() != null) {
                URI adminUri = httpServerInfo.getAdminUri();
                if (adminUri.getScheme().equals("http")) {
                    builder.addProperty("http", adminUri.toString());
                    builder.addProperty("http-external", httpServerInfo.getAdminExternalUri().toString());
                } else if (adminUri.getScheme().equals("https")) {
                    builder.addProperty("https", adminUri.toString());
                    builder.addProperty("https-external", httpServerInfo.getAdminExternalUri().toString());
                }
            }
            if (nodeInfo.getBinarySpec() != null) {
                builder.addProperty("binary", nodeInfo.getBinarySpec());
            }
            if (nodeInfo.getConfigSpec() != null) {
                builder.addProperty("config", nodeInfo.getConfigSpec());
            }
            return builder.build();
        }
    }
}
