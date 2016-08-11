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
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import io.airlift.log.Logger;

import javax.management.remote.JMXServiceURL;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.util.Properties;

class JmxAgent9
    implements JmxAgent
{
    private static final Logger log = Logger.get(JmxAgent.class);

    private final JMXServiceURL url;

    @Inject
    public JmxAgent9(JmxConfig config)
            throws IOException
    {
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

        try {
            VirtualMachine virtualMachine = VirtualMachine.attach(Long.toString(getProcessId()));
            try {
                virtualMachine.startLocalManagementAgent();

                Properties properties = new Properties();
                properties.setProperty("com.sun.management.jmxremote.port", Integer.toString(registryPort));
                properties.setProperty("com.sun.management.jmxremote.rmi.port", Integer.toString(serverPort));
                properties.setProperty("com.sun.management.jmxremote.authenticate", "false");
                properties.setProperty("com.sun.management.jmxremote.ssl", "false");
                virtualMachine.startManagementAgent(properties);
            }
            finally {
                virtualMachine.detach();
            }
        }
        catch (AttachNotSupportedException e) {
            throw Throwables.propagate(e);
        }

        HostAndPort address;
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

        this.url = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", address.getHostText(), address.getPort()));
    }

    public JMXServiceURL getUrl()
    {
        return url;
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
