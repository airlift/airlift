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
package com.proofpoint.discovery.client;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.weakref.jmx.guice.MBeanModule;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.management.MBeanServer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDiscoveryModule
{
    @Test
    public void testBinding()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of("testing.discovery.uri", "fake://server"))),
                new JsonModule(),
                new TestingNodeModule(),
                new MBeanModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                    }
                },
                new DiscoveryModule()
        );

        // should produce a discovery announcement client and a lookup client
        Assert.assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        Assert.assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        Assert.assertNotNull(injector.getInstance(Announcer.class));
    }

    @Test
    public void testExecutorShutdown()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new JsonModule(),
                        new TestingNodeModule(),
                        new DiscoveryModule(),
                        new ReportingModule(),
                        new TestingMBeanModule()
                );

        Injector injector = app.initialize();

        ExecutorService executor = injector.getInstance(Key.get(ScheduledExecutorService.class, ForDiscoveryClient.class));
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        assertFalse(executor.isShutdown());
        lifeCycleManager.stop();
        assertTrue(executor.isShutdown());
    }
}
