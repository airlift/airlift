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
package com.proofpoint.jmx;


import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import sun.management.Agent;

import javax.annotation.PostConstruct;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;

public class JmxAgent
{
    private final int registryPort;
    private final int serverPort;

    private static final Logger log = Logger.get(JmxAgent.class);
    private final JMXServiceURL url;

    @Inject
    public JmxAgent(JmxConfig config)
            throws IOException
    {
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
            // This is how the jdk jmx agent constructs its url
            url = new JMXServiceURL("rmi", null, registryPort);
        }
        catch (MalformedURLException e) {
            // should not happen...
            throw new AssertionError(e);
        }
    }

    public JMXServiceURL getURL()
    {
        return url;
    }

    @PostConstruct
    public void start()
            throws IOException
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
            throw Throwables.propagate(e);
        }

        log.info("JMX Agent listening on %s:%s", url.getHost(), url.getPort());
    }
}
