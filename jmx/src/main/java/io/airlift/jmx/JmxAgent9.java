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

import com.google.common.net.HostAndPort;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import io.airlift.log.Logger;

import javax.inject.Inject;
import javax.management.remote.JMXServiceURL;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import java.util.Properties;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

class JmxAgent9
        implements JmxAgent
{
    private static final Logger log = Logger.get(JmxAgent.class);

    private static final String JMX_REGISTRY_PORT = "com.sun.management.jmxremote.port";
    private static final String JMX_SERVER_PORT = "com.sun.management.jmxremote.rmi.port";
    private static final String ALLOW_SELF_ATTACH = "jdk.attach.allowAttachSelf";

    private final JMXServiceURL url;

    @Inject
    public JmxAgent9(JmxConfig config)
            throws IOException
    {
        int registryPort = requireNonNull(config.getRmiRegistryPort(), "RMI registry port is not configured");
        Integer existingRegistryPort = Integer.getInteger(JMX_REGISTRY_PORT);

        if (existingRegistryPort != null) {
            if (existingRegistryPort != registryPort) {
                throw new RuntimeException(format(
                        "System property '%s=%s' does match configured RMI registry port %s",
                        JMX_REGISTRY_PORT, existingRegistryPort, registryPort));
            }
            if (existingRegistryPort.equals(0)) {
                throw new RuntimeException(format(
                        "JMX agent already running on an unknown port (system property '%s' is 0)",
                        JMX_REGISTRY_PORT));
            }
        }

        int serverPort = 0;
        Integer existingServerPort = Integer.getInteger(JMX_SERVER_PORT);
        Integer configuredServerPort = config.getRmiServerPort();
        if (!Objects.equals(existingServerPort, configuredServerPort)) {
            throw new RuntimeException(format(
                    "System property '%s=%s' does match configured RMI server port %s",
                    JMX_SERVER_PORT, existingServerPort, configuredServerPort));
        }
        if (configuredServerPort != null && !configuredServerPort.equals(0)) {
            serverPort = configuredServerPort;
        }

        // this is how the JDK JMX agent constructs its URL
        JMXServiceURL jmxUrl = new JMXServiceURL("rmi", null, registryPort);
        HostAndPort address = HostAndPort.fromParts(jmxUrl.getHost(), jmxUrl.getPort());

        if (existingRegistryPort == null) {
            startJmxAgent(registryPort, serverPort);
            log.info("JMX agent started and listening on %s", address);
        }
        else {
            log.info("JMX agent already running and listening on %s", address);
        }

        this.url = new JMXServiceURL(format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", address.getHost(), address.getPort()));
    }

    public JMXServiceURL getUrl()
    {
        return url;
    }

    private static void startJmxAgent(int registryPort, int serverPort)
            throws IOException
    {
        try {
            VirtualMachine virtualMachine = VirtualMachine.attach(Long.toString(getProcessId()));
            try {
                virtualMachine.startLocalManagementAgent();

                Properties properties = new Properties();
                properties.setProperty(JMX_REGISTRY_PORT, Integer.toString(registryPort));
                properties.setProperty(JMX_SERVER_PORT, Integer.toString(serverPort));
                properties.setProperty("com.sun.management.jmxremote.authenticate", "false");
                properties.setProperty("com.sun.management.jmxremote.ssl", "false");
                virtualMachine.startManagementAgent(properties);
            }
            finally {
                virtualMachine.detach();
            }
        }
        catch (AttachNotSupportedException e) {
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            if (!Boolean.getBoolean(ALLOW_SELF_ATTACH)) {
                throw new IOException(format("%s (try adding '-D%s=true' to the JVM config)", e, ALLOW_SELF_ATTACH));
            }
            throw e;
        }
    }

    private static long getProcessId()
    {
        // TODO: replace with ProcessHandle.current().getPid() in Java 9
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int index = name.indexOf('@');

        if (index < 1) {
            throw new AssertionError("Cannot get process PID");
        }

        try {
            return Long.parseLong(name.substring(0, index));
        }
        catch (NumberFormatException e) {
            throw new AssertionError("Cannot get process PID");
        }
    }

    public static void main(String[] args)
            throws IOException
    {
        new JmxAgent9(new JmxConfig());
        new JmxAgent9(new JmxConfig());
    }
}
