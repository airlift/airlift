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
package io.airlift.discovery.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.discovery.client.testing.InMemoryDiscoveryClient;
import io.airlift.node.NodeInfo;
import io.airlift.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;

public class TestCachingServiceSelector
{
    private static final ServiceDescriptor APPLE_1_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));
    private static final ServiceDescriptor APPLE_2_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));
    private static final ServiceDescriptor DIFFERENT_TYPE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "banana", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("b", "banana"));
    private static final ServiceDescriptor DIFFERENT_POOL = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "fool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));

    private ScheduledExecutorService executor;
    private NodeInfo nodeInfo;

    @BeforeClass
    protected void setUp()
            throws Exception
    {
        executor = new ScheduledThreadPoolExecutor(10, daemonThreadsNamed("Discovery-%s"));
        nodeInfo = new NodeInfo("environment");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
            throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void testBasics()
    {
        CachingServiceSelector serviceSelector = new CachingServiceSelector("type",
                new ServiceSelectorConfig().setPool("pool"),
                new InMemoryDiscoveryClient(nodeInfo),
                executor);

        Assert.assertEquals(serviceSelector.getType(), "type");
        Assert.assertEquals(serviceSelector.getPool(), "pool");
    }

    @Test
    public void testNotStartedEmpty()
    {
        CachingServiceSelector serviceSelector = new CachingServiceSelector("type",
                new ServiceSelectorConfig().setPool("pool"),
                new InMemoryDiscoveryClient(nodeInfo),
                executor);

        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testStartedEmpty()
            throws Exception
    {
        CachingServiceSelector serviceSelector = new CachingServiceSelector("type",
                new ServiceSelectorConfig().setPool("pool"),
                new InMemoryDiscoveryClient(nodeInfo),
                executor);

        serviceSelector.start();

        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testNotStartedWithServices()
    {
        InMemoryDiscoveryClient discoveryClient = new InMemoryDiscoveryClient(nodeInfo);
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        CachingServiceSelector serviceSelector = new CachingServiceSelector("apple",
                new ServiceSelectorConfig().setPool("pool"),
                discoveryClient,
                executor);

        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testStartedWithServices()
            throws Exception
    {
        InMemoryDiscoveryClient discoveryClient = new InMemoryDiscoveryClient(nodeInfo);
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        CachingServiceSelector serviceSelector = new CachingServiceSelector("apple",
                new ServiceSelectorConfig().setPool("pool"),
                discoveryClient,
                executor);

        serviceSelector.start();

        Thread.sleep(100);

        Assertions.assertEqualsIgnoreOrder(serviceSelector.selectAllServices(), ImmutableList.of(APPLE_1_SERVICE, APPLE_2_SERVICE));
    }
}
