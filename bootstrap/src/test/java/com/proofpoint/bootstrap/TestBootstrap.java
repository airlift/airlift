package com.proofpoint.bootstrap;

import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.spi.InstanceBinding;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.Test;

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

    public static class Instance
    {
    }

    public static class InstanceA
    {
        @Inject
        public InstanceA(InstanceB b)
        {
        }
    }

    public static class InstanceB
    {
        @Inject
        public InstanceB(InstanceA a)
        {
        }
    }
}
