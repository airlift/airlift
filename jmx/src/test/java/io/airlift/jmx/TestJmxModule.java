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

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.Test;
import org.weakref.jmx.guice.MBeanModule;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import static io.airlift.testing.Assertions.assertGreaterThan;

public class TestJmxModule
{
    @Test
    public void testModule()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new TestingNodeModule(),
                new JmxModule(),
                new MBeanModule());

        Injector injector = app
                .strictConfig()
                .doNotInitializeLogging()
                .initialize();

        JmxAgent agent = injector.getInstance(JmxAgent.class);

        JMXServiceURL url = agent.getUrl();

        JMXConnector connector = JMXConnectorFactory.connect(url);
        connector.connect();

        MBeanServerConnection connection = connector.getMBeanServerConnection();
        assertGreaterThan(connection.getMBeanCount(), 0);
    }
}
