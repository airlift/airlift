package com.proofpoint.jmx;

import com.google.inject.Inject;
import com.proofpoint.log.Logger;

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

public class JMXAgent
{
    private final String host;
    private final int registryPort;
    private final int serverPort;
    private JMXConnectorServer connectorServer;

    private static Logger log = Logger.get(JMXAgent.class);
    private JMXServiceURL url;

    @Inject
    public JMXAgent(MBeanServer server, JMXConfig config)
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
