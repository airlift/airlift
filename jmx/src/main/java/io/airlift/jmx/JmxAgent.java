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
import sun.management.jmxremote.ConnectorBootstrap;
import sun.rmi.server.UnicastRef;

import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.rmi.server.RemoteObject;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

public class JmxAgent
{
    private static final Logger log = Logger.get(JmxAgent.class);

    private final HostAndPort address;

    @Inject
    public JmxAgent(JmxConfig config)
            throws IOException
    {
        // first, see if the jmx agent is already started (e.g., via command line properties passed to the jvm)
        HostAndPort address = getRunningAgentAddress(config.getRmiRegistryPort(), config.getRmiServerPort());
        if (address != null) {
            log.info("JMX agent already running and listening on %s", address);
        }
        else {
            // otherwise, start it manually
            int registryPort;
            if (config.getRmiRegistryPort() == null) {
                registryPort = NetUtils.findUnusedPort();
            }
            else {
                registryPort = config.getRmiRegistryPort();
            }

            int serverPort = 0;
            if (config.getRmiServerPort() != null) {
                serverPort = config.getRmiServerPort();
            }

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
                throw Throwables.propagate(e);
            }

            try {
                // This is how the jdk jmx agent constructs its url
                JMXServiceURL url = new JMXServiceURL("rmi", null, registryPort);
                address = HostAndPort.fromParts(url.getHost(), url.getPort());
            }
            catch (MalformedURLException e) {
                // should not happen...
                throw new AssertionError(e);
            }

            log.info("JMX agent started and listening on %s", address);
        }

        this.address = address;
    }

    private static HostAndPort getRunningAgentAddress(Integer registryPort, Integer serverPort)
    {
        try {
            JMXConnectorServer jmxServer = getField(Agent.class, JMXConnectorServer.class, "jmxServer");
            RemoteObject registry = getField(ConnectorBootstrap.class, RemoteObject.class, "registry");

            if (jmxServer != null && registry != null) {
                int actualRegistryPort = ((UnicastRef) registry.getRef()).getLiveRef().getPort();

                checkState(actualRegistryPort > 0, "Expected actual RMI registry port to be > 0, actual: %s", actualRegistryPort);

                // if registry port and server port were configured and the agent is already running, make sure
                // the configuration agrees to avoid surprises
                if (registryPort != null && registryPort != 0) {
                    checkArgument(actualRegistryPort == registryPort, "JMX agent is already running, but actual RMI registry port (%s) doesn't match configured port (%s)", actualRegistryPort, registryPort);
                }

                if (serverPort != null && serverPort != 0) {
                    int actualServerPort = jmxServer.getAddress().getPort();
                    checkArgument(actualServerPort == serverPort, "JMX agent is already running, but actual RMI server port (%s) doesn't match configured port (%s)", actualServerPort, serverPort);
                }

                return HostAndPort.fromParts(jmxServer.getAddress().getHost(), actualRegistryPort);
            }
        }
        catch (Exception e) {
            log.warn(e, "Cannot determine if JMX agent is already running. Will try to start it manually.");
        }

        return null;
    }

    public HostAndPort getAddress()
    {
        return address;
    }

    private static <T> T getField(Class<?> clazz, Class<T> returnType, String name)
            throws Exception
    {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        try {
            return returnType.cast(field.get(clazz));
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException(format("Field %s in class %s is not of type %s, actual: %s", name, clazz.getName(), returnType.getName(), field.getType().getName()), e);
        }
    }
}
