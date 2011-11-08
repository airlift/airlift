package com.proofpoint.jmx.http.rpc;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.proofpoint.discovery.client.ServiceAnnouncement;
import com.proofpoint.discovery.client.ServiceAnnouncement.ServiceAnnouncementBuilder;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.node.NodeInfo;

import javax.servlet.Servlet;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Map;

import static com.proofpoint.configuration.ConfigurationModule.bindConfig;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceAnnouncement.serviceAnnouncement;

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
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(Servlet.class).annotatedWith(bindingAnnotation).to(MBeanServerServlet.class).in(Scopes.SINGLETON);
        binder.bind(new TypeLiteral<Map<String, String>>() {}).annotatedWith(bindingAnnotation).toInstance(ImmutableMap.<String, String>of());

        ServiceAnnouncementBuilder serviceAnnouncementBuilder = serviceAnnouncement("jmx-http-rpc");
        discoveryBinder(binder).bindServiceAnnouncement(new JmxHttpRpcAnnouncementProvider(serviceAnnouncementBuilder));

        bindConfig(binder).to(JmxHttpRpcConfig.class);
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
                } else if (adminUri.getScheme().equals("https")) {
                    builder.addProperty("https", adminUri.toString());
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
