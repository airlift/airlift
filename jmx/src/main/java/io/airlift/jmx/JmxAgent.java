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
import com.google.common.net.HostAndPort;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import sun.management.Agent;

import javax.management.remote.JMXServiceURL;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

public class JmxAgent
{
    private static final Logger log = Logger.get(JmxAgent.class);

    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private final JMXServiceURL url;

    @Inject
    public JmxAgent(JmxConfig config)
            throws IOException
    {
        if (STARTED.getAndSet(true)) {
            throw new RuntimeException("JMX agent already started in this JVM");
        }

        int registryPort;
        if (config.getRmiRegistryPort() == null) {
            registryPort = findUnusedPort();
        }
        else {
            registryPort = config.getRmiRegistryPort();
        }

        int serverPort = 0;
        if (config.getRmiServerPort() != null) {
            serverPort = config.getRmiServerPort();
        }

        startAgent(registryPort, serverPort);

        HostAndPort address = getJmxAddress(registryPort);

        log.info("JMX agent started and listening on %s", address);

        this.url = new JMXServiceURL(format("service:jmx:rmi:///jndi/rmi://%s/jmxrmi", address));

    }

    public JMXServiceURL getUrl()
    {
        return url;
    }

    private static void startAgent(int registryPort, int serverPort)
    {
        // This is somewhat of a hack, but the jmx agent in Oracle/OpenJDK doesn't
        // have a programmatic API for starting it and controlling its parameters
        System.setProperty("com.sun.management.jmxremote", "true");
        System.setProperty("com.sun.management.jmxremote.port", Integer.toString(registryPort));
        System.setProperty("com.sun.management.jmxremote.rmi.port", Integer.toString(serverPort));
        System.setProperty("com.sun.management.jmxremote.authenticate", "false");
        System.setProperty("com.sun.management.jmxremote.ssl", "false");

        try {
            Agent.startAgent();
        }
        catch (Exception e) {
            throw new RuntimeException("Error starting JMX agent (already running?)", e);
        }
    }

    private static HostAndPort getJmxAddress(int registryPort)
    {
        try {
            // This is how the jdk jmx agent constructs its url
            JMXServiceURL url = new JMXServiceURL("rmi", null, registryPort);
            return HostAndPort.fromParts(url.getHost(), url.getPort());
        }
        catch (MalformedURLException e) {
            // should not happen...
            throw Throwables.propagate(e);
        }
    }

    private static int findUnusedPort()
            throws IOException
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
