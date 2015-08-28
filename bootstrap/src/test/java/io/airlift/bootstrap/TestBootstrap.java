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
package io.airlift.bootstrap;

import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import io.airlift.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;


public class TestBootstrap
{
    @Test
    public void testRequiresExplicitBindings()
            throws Exception
    {
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.initialize().getInstance(Instance.class);
            Assert.fail("should require explicit bindings");
        }
        catch (ConfigurationException e) {
            Assertions.assertContains(e.getErrorMessages().iterator().next().getMessage(), "Explicit bindings are required");
        }
    }

    @Test
    public void testDoesNotAllowCircularDependencies()
            throws Exception
    {
        Bootstrap bootstrap = new Bootstrap(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(InstanceA.class);
                binder.bind(InstanceB.class);
            }
        });

        try {
            bootstrap.initialize().getInstance(InstanceA.class);
            Assert.fail("should not allow circular dependencies");
        }
        catch (ProvisionException e) {
            Assertions.assertContains(e.getErrorMessages().iterator().next().getMessage(), "circular proxies are disabled");
        }
    }

    @Test
    public void testInitializerBeforeStart()
            throws Exception
    {
        Bootstrap bootstrap = new Bootstrap(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(InstanceC.class);
                binder.bind(InstanceD.class);
            }
        });

        Injector injector = bootstrap.addInitializerBeforeStart(
                inj -> inj.getInstance(InstanceC.class).init()
        ).initialize();
        InstanceC instanceC = injector.getInstance(InstanceC.class);
        InstanceD instanceD = injector.getInstance(InstanceD.class);

        Assertions.assertLessThan(instanceC.creationTime, instanceD.creationTime);
    }

    public static class Instance {}

    public static class InstanceA
    {
        @Inject
        public InstanceA(InstanceB b) { }
    }

    public static class InstanceB
    {
        @Inject
        public InstanceB(InstanceA a) { }
    }

    public static class InstanceC
    {
        public long creationTime;

        @Inject
        public InstanceC() { }

        public void init()
        {
            creationTime = System.nanoTime();
        }
    }

    public static class InstanceD
    {
        public long creationTime;

        @Inject
        public InstanceD() { }

        @PostConstruct
        public void init()
        {
            creationTime = System.nanoTime();
        }
    }
}
