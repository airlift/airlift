package io.airlift.bootstrap;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;

public class TestLifecycledBindingBuilder
{
    @Inject
    private LifeCycleManager manager;

    @Test
    public void testWrapNonInjectingProvider()
        throws Exception
    {
        final NonInjectingFooProvider provider = new NonInjectingFooProvider();

        Injector inj = new Bootstrap(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                LifecycledBindingBuilder.lifecycleBinding(binder, Foo.class).toProvider(provider);
            }
        })
            .doNotInitializeLogging()
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        assertEquals(provider.getInjectCalled(), 1);
        assertEquals(provider.getPostConstructCalled(), 2);
        assertEquals(provider.getCount(), 2);

        assertEquals(provider.getPreDestroyCalled(), 0);

        Foo foo1 = inj.getInstance(Foo.class);
        assertEquals(foo1.getPostConstructCalled(), 1);
        assertEquals(foo1.getCount(), 1);

        // Calling the injector is job of the provider,
        // not the lifecycle.
        assertEquals(foo1.getInjectCalled(), 0);
        assertEquals(foo1.getPreDestroyCalled(), 0);

        Foo foo2 = inj.getInstance(Foo.class);

        assertNotSame(foo2, foo1);

        assertEquals(foo2.getPostConstructCalled(), 1);
        assertEquals(foo2.getCount(), 1);

        assertEquals(foo2.getInjectCalled(), 0);
        assertEquals(foo2.getPreDestroyCalled(), 0);

        manager.stop();

        assertEquals(provider.getPreDestroyCalled(), 3);
        assertEquals(provider.getCount(), 3);

        assertEquals(foo1.getPreDestroyCalled(), 2);
        assertEquals(foo1.getCount(), 2);

        assertEquals(foo2.getPreDestroyCalled(), 2);
        assertEquals(foo2.getCount(), 2);
    }

    @Test
    public void testWrapNonInjectingProviderSingleton()
        throws Exception
    {
        Injector inj = new Bootstrap(new Module() {
            @Override
            public void configure(Binder binder)
            {
                LifecycledBindingBuilder.lifecycleBinding(binder, Foo.class).toProvider(NonInjectingFooProvider.class).in(Scopes.SINGLETON);
                binder.bind(NonInjectingFooProvider.class).in(Scopes.SINGLETON);
            }
        })
            .doNotInitializeLogging()
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        NonInjectingFooProvider provider = inj.getInstance(NonInjectingFooProvider.class);

        assertEquals(provider.getInjectCalled(), 1);
        assertEquals(provider.getPostConstructCalled(), 2);
        assertEquals(provider.getCount(), 2);

        assertEquals(provider.getPreDestroyCalled(), 0);

        Foo foo1 = inj.getInstance(Foo.class);
        assertEquals(foo1.getPostConstructCalled(), 1);
        assertEquals(foo1.getCount(), 1);

        // Calling the injector is job of the provider,
        // not the lifecycle.
        assertEquals(foo1.getInjectCalled(), 0);
        assertEquals(foo1.getPreDestroyCalled(), 0);

        Foo foo2 = inj.getInstance(Foo.class);

        assertSame(foo2, foo1);

        manager.stop();

        assertEquals(provider.getPreDestroyCalled(), 3);
        assertEquals(provider.getCount(), 3);

        assertEquals(foo1.getPreDestroyCalled(), 2);
        assertEquals(foo1.getCount(), 2);
    }

    @Test
    public void testWrapInjectingProvider()
        throws Exception
    {
        final InjectingFooProvider provider = new InjectingFooProvider();

        Injector inj = new Bootstrap(new Module() {
            @Override
            public void configure(Binder binder)
            {
                LifecycledBindingBuilder.lifecycleBinding(binder, Foo.class).toProvider(provider);
            }
        })
            .doNotInitializeLogging()
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        assertEquals(provider.getInjectCalled(), 1);
        assertEquals(provider.getPostConstructCalled(), 2);
        assertEquals(provider.getCount(), 2);

        assertEquals(provider.getPreDestroyCalled(), 0);

        Foo foo1 = inj.getInstance(Foo.class);
        assertEquals(foo1.getInjectCalled(), 1);
        assertEquals(foo1.getPostConstructCalled(), 2);
        assertEquals(foo1.getCount(), 2);

        assertEquals(foo1.getPreDestroyCalled(), 0);

        Foo foo2 = inj.getInstance(Foo.class);

        assertNotSame(foo2, foo1);

        assertEquals(foo2.getInjectCalled(), 1);
        assertEquals(foo2.getPostConstructCalled(), 2);
        assertEquals(foo2.getCount(), 2);

        assertEquals(foo2.getPreDestroyCalled(), 0);

        manager.stop();

        assertEquals(provider.getPreDestroyCalled(), 3);
        assertEquals(provider.getCount(), 3);

        assertEquals(foo1.getPreDestroyCalled(), 3);
        assertEquals(foo1.getCount(), 3);

        assertEquals(foo2.getPreDestroyCalled(), 3);
        assertEquals(foo2.getCount(), 3);
    }

    @Test
    public void testWrapInjectingProviderSingleton()
        throws Exception
    {
        Injector inj = new Bootstrap(new Module() {
            @Override
            public void configure(Binder binder)
            {
                LifecycledBindingBuilder.lifecycleBinding(binder, Foo.class).toProvider(InjectingFooProvider.class).in(Scopes.SINGLETON);
                binder.bind(InjectingFooProvider.class).in(Scopes.SINGLETON);
            }
        })
            .doNotInitializeLogging()
            .strictConfig()
            .initialize();

        inj.injectMembers(this);

        InjectingFooProvider provider = inj.getInstance(InjectingFooProvider.class);

        assertEquals(provider.getInjectCalled(), 1);
        assertEquals(provider.getPostConstructCalled(), 2);
        assertEquals(provider.getCount(), 2);

        assertEquals(provider.getPreDestroyCalled(), 0);

        Foo foo1 = inj.getInstance(Foo.class);
        assertEquals(foo1.getInjectCalled(), 1);
        assertEquals(foo1.getPostConstructCalled(), 2);
        assertEquals(foo1.getCount(), 2);

        assertEquals(foo1.getPreDestroyCalled(), 0);

        Foo foo2 = inj.getInstance(Foo.class);

        assertSame(foo2, foo1);

        manager.stop();

        assertEquals(provider.getPreDestroyCalled(), 3);
        assertEquals(provider.getCount(), 3);

        assertEquals(foo1.getPreDestroyCalled(), 3);
        assertEquals(foo1.getCount(), 3);
    }

    private static class NonInjectingFooProvider
        extends AbstractLifeCycleChecker
        implements Provider<Foo>
    {
        public Foo get()
        {
            return new Foo();
        }
    }

    private static class InjectingFooProvider
        extends AbstractLifeCycleChecker
        implements Provider<Foo>
    {
        private Injector injector;

        @Inject
        void setInject(Injector injector)
        {
            this.injector = injector;
        }

        public Foo get()
        {
            Foo foo = new Foo();
            Assert.assertNotNull(injector);
            injector.injectMembers(foo);
            return foo;
        }
    }

    private static class Foo extends AbstractLifeCycleChecker
    {
    }
}
