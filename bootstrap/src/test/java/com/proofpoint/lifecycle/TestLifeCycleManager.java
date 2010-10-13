package com.proofpoint.lifecycle;

import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Scopes;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestLifeCycleManager
{
    private final static List<String>      stateLog = new CopyOnWriteArrayList<String>();

    @BeforeMethod
    public void     setup()
    {
        stateLog.clear();
    }

    public static void      note(String str)
    {
        // I'm assuming that tests are run serially
        stateLog.add(str);
    }

    @Test
    public void     testPrivateModule() throws Exception
    {
        Module      module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                final PrivateModule privateModule = new PrivateModule()
                {
                    @Override
                    protected void configure()
                    {
                        binder().bind(SimpleBase.class).to(SimpleBaseImpl.class);
                        binder().expose(SimpleBaseImpl.class);
                    }
                };
                binder.install(privateModule);
            }
        };

        Injector            injector = Guice.createInjector(new LifeCycleModule(Elements.getElements(module)));
        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postSimpleBaseImpl", "preSimpleBaseImpl"));
    }

    @Test
    public void testSubClassAnnotated() throws Exception
    {
        Module      module = new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                binder.bind(SimpleBase.class).to(SimpleBaseImpl.class);
            }
        };
        Injector            injector = Guice.createInjector(new LifeCycleModule(Elements.getElements(module)));
        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postSimpleBaseImpl", "preSimpleBaseImpl"));
    }

    @Test
    public void testExecuted() throws Exception
    {
        Injector            injector = Guice.createInjector(new LifeCycleModule(new ArrayList<Element>()));
        ExecutedInstance    instance = injector.getInstance(ExecutedInstance.class);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        instance.waitForStart();
        lifeCycleManager.stop();
        instance.waitForEnd();

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
        Injector            injector = Guice.createInjector(new LifeCycleModule(Elements.getElements(module)), module);

        injector.getInstance(AnotherInstance.class);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Assert.assertEquals(stateLog, Arrays.asList("postDependentInstance", "preDependentInstance"));
    }

    @Test
    public void testJITInjection() throws Exception
    {
        Injector            injector = Guice.createInjector(new LifeCycleModule(new ArrayList<Element>()));
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
        Injector            injector = Guice.createInjector(new LifeCycleModule(Elements.getElements(module)), module);

        LifeCycleManager    lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        lifeCycleManager.start();
        lifeCycleManager.stop();

        Set<String>     stateLogSet = Sets.newHashSet(stateLog);
        Assert.assertEquals(stateLogSet, Sets.newHashSet("postDependentBoundInstance", "postDependentInstance", "postMakeOne", "postMakeTwo", "preDestroyTwo", "preDestroyOne", "preDependentInstance", "preDependentBoundInstance"));
    }
}
