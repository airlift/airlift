package com.proofpoint.jmx.http.rpc;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;
import java.net.MalformedURLException;
import java.util.Map;

public class HttpJmxConnectorProvider implements JMXConnectorProvider
{
    @Override
    public JMXConnector newJMXConnector(JMXServiceURL jmxServiceURL, Map<String, ?> environment)
            throws MalformedURLException
    {
        return new HttpJmxConnector(jmxServiceURL, environment);
    }
}
