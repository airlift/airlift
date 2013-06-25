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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsUpdater;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.node.NodeInfo;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestHttpServiceUpdaterAdapter
{
    private static final ServiceDescriptor APPLE_1_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-a.example.com"));
    private static final ServiceDescriptor APPLE_2_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-c.example.com", "https", "https://apple-b.example.com"));
    private static final ServiceDescriptor DIFFERENT_TYPE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "banana", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("https", "https://banana.example.com"));
    private static final ServiceDescriptor DIFFERENT_POOL = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "fool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-fool.example.com"));

    private ScheduledExecutorService executor;
    private NodeInfo nodeInfo;
    private InMemoryDiscoveryClient discoveryClient;
    private HttpServiceBalancerImpl httpServiceBalancer;
    private ServiceDescriptorsUpdater updater;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        executor = new ScheduledThreadPoolExecutor(10,
                new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
        nodeInfo = new NodeInfo("environment");
        discoveryClient = new InMemoryDiscoveryClient(nodeInfo);
        httpServiceBalancer = mock(HttpServiceBalancerImpl.class);
        updater = new ServiceDescriptorsUpdater(new HttpServiceUpdaterAdapter(httpServiceBalancer),
                "apple",
                new ServiceSelectorConfig().setPool("pool"),
                discoveryClient,
                executor);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void testNotStartedEmpty()
    {
        verifyNoMoreInteractions(httpServiceBalancer);
    }

    @Test
    public void testStartedEmpty()
            throws Exception
    {
        updater.start();

        verifyNoMoreInteractions(httpServiceBalancer);
    }

    @Test
    public void testNotStartedWithServices()
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        verifyNoMoreInteractions(httpServiceBalancer);
    }

    @Test
    public void testStartedWithServices()
            throws Exception
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        updater.start();

        Thread.sleep(100);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        verify(httpServiceBalancer).updateHttpUris(captor.capture());

        assertEquals(captor.getValue(), ImmutableSet.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-b.example.com")));
    }
}
