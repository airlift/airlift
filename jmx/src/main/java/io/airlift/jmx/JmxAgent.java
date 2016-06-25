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

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import io.airlift.log.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.GuardedBy;
import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class JmxAgent
    implements Closeable
{
    private static final Logger log = Logger.get(JmxAgent.class);

    private final JMXServiceURL url;
    private final int registryPort;
    private final String host;
    private final MBeanServer mBeanServer;

    @GuardedBy("this")
    private JMXConnectorServer connectorServer;

    @GuardedBy("this")
    private Registry registry;

    @Inject
    public JmxAgent(JmxConfig config, MBeanServer mBeanServer)
            throws IOException
    {
        this.mBeanServer = mBeanServer;

        if (config.getRmiRegistryPort() == null) {
            registryPort = NetUtils.findUnusedPort();
        }
        else {
            registryPort = config.getRmiRegistryPort();
        }

        int rmiPort = 0;
        if (config.getRmiServerPort() != null) {
            rmiPort = config.getRmiServerPort();
        }

        host = InetAddress.getLocalHost().getHostName();
        url = new JMXServiceURL(String.format("service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/jmxrmi", host, rmiPort, host, registryPort));
    }

    @PostConstruct
    public synchronized void start()
    {
        try {
            registry = LocateRegistry.createRegistry(registryPort);
            connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mBeanServer);
            connectorServer.start();

            log.info("JMX agent started and listening on %s:%s", host, registryPort);
        }
        catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    public JMXServiceURL getUrl()
    {
        return url;
    }

    @PreDestroy
    public synchronized void close()
            throws IOException
    {
        connectorServer.stop();
        UnicastRemoteObject.unexportObject(registry, true);
    }
}
