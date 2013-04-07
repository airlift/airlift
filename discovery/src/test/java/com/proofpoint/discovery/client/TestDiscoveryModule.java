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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDiscoveryModule
{
    @Test
    public void testBinding()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of("discovery.uri", "fake://server"))),
                new JsonModule(),
                new TestingNodeModule(),
                new DiscoveryModule()
        );

        // should produce a discovery announcement client and a lookup client
        Assert.assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        Assert.assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        Assert.assertNotNull(injector.getInstance(Announcer.class));
    }

}
