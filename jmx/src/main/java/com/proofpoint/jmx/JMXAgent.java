package com.proofpoint.jmx;

import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.net.NetUtils;

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
    private JMXConnectorServer connectorServer;

    private static Logger log = Logger.get(JMXAgent.class);
    private JMXServiceURL url;

    @Inject
    public JMXAgent(MBeanServer server, String host, Integer registryPort, Integer serverPort)
            throws IOException
    {
        if (host == null) {
            host = InetAddress.getLocalHost().getHostAddress();
        }
        if (registryPort == null) {
            registryPort = NetUtils.findUnusedPort();
        }
        if (serverPort == null) {
            serverPort = NetUtils.findUnusedPort();
        }

        this.host = host;
        this.registryPort = registryPort;

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
    public void stop() throws IOException
    {
        connectorServer.stop();
    }
}
