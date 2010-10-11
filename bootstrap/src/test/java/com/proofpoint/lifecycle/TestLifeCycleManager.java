package com.proofpoint.lifecycle;

import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class TestLifeCycleManager
{
    private final static List<String>      stateLog = Lists.newArrayList();

    @BeforeMethod
    public void     setup()
    {
        stateLog.clear();
    }

    public synchronized static void      note(String str)
    {
        // I'm assuming that tests are run serially
        stateLog.add(str);
    }

    @Test
    public void testExecuted() throws Exception
    {
        Injector            injector = Guice.createInjector(new LifeCycleModule());
        ExecutedInstance    instance = injector.getInstance(ExecutedInstance.class);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        instance.waitForRun();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("Starting", "Done"));
    }

    @Test
    public void testDeepDependency() throws Exception
    {
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(AnotherInstance.class).in(Scopes.SINGLETON);
            }
        };
        Injector            injector = Guice.createInjector(new LifeCycleModule(module), module);

        injector.getInstance(AnotherInstance.class);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postDependentInstance", "preDependentInstance"));
    }

    @Test
    public void testJITInjection() throws Exception
    {
        Injector            injector = Guice.createInjector(new LifeCycleModule());
        injector.getInstance(AnInstance.class);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postDependentInstance", "preDependentInstance"));
    }

    @Test
    public void testModule() throws Exception
    {
        Module module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(DependentBoundInstance.class).to(DependentInstanceImpl.class).in(Scopes.SINGLETON);

                binder.bind(InstanceOne.class).in(Scopes.SINGLETON);
                binder.bind(InstanceTwo.class).in(Scopes.SINGLETON);
            }
        };
        Injector            injector = Guice.createInjector(new LifeCycleModule(module), module);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postDependentBoundInstance", "postDependentInstance", "postMakeOne", "postMakeTwo", "preDestroyTwo", "preDestroyOne", "preDependentInstance", "preDependentBoundInstance"));
    }
}
