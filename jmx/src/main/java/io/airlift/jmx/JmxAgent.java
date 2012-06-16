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

import com.google.inject.Inject;
import io.airlift.log.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.rmi.registry.LocateRegistry;
import java.util.Collections;

public class JmxAgent
{
    private final String host;
    private final int registryPort;
    private final int serverPort;
    private final JMXConnectorServer connectorServer;

    private static Logger log = Logger.get(JmxAgent.class);
    private final JMXServiceURL url;

    @Inject
    public JmxAgent(MBeanServer server, JmxConfig config)
            throws IOException
    {
        if (config.getHostname() == null) {
            host = InetAddress.getLocalHost().getHostAddress();
        }
        else {
            host = config.getHostname();
        }

        if (config.getRmiRegistryPort() == null) {
            registryPort = NetUtils.findUnusedPort();
        }
        else {
            registryPort = config.getRmiRegistryPort();
        }

        if (config.getRmiServerPort() == null) {
            serverPort = NetUtils.findUnusedPort();
        }
        else {
            serverPort = config.getRmiServerPort();
        }

        try {
            url = new JMXServiceURL(String.format("service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/jmxrmi",
                    host, serverPort, host, registryPort));
        }
        catch (MalformedURLException e) {
            // should not happen...
            throw new AssertionError(e);
        }

        connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, Collections.<String, Object>emptyMap(), server);
    }

    public JMXServiceURL getURL()
    {
        return url;
    }

    @PostConstruct
    public void start()
            throws IOException
    {
        System.setProperty("java.rmi.server.randomIDs", "true");
        System.setProperty("java.rmi.server.hostname", host);

        LocateRegistry.createRegistry(registryPort);

        connectorServer.start();

        log.info("JMX Agent listening on %s:%s", host, registryPort);
    }

    @PreDestroy
    public void stop()
            throws IOException
    {
        connectorServer.stop();
    }
}
