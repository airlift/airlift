package io.airlift.event.client;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import org.testng.annotations.BeforeMethod;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class TestMultiEventModule
        extends AbstractTestMultiEventClient
{
    @BeforeMethod
    public void setUp()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new InMemoryEventModule(),
                binder -> {
                    binder.bind(AnotherInMemoryEventClient.class).in(Scopes.SINGLETON);
                    newSetBinder(binder, EventClient.class).addBinding().to(Key.get(AnotherInMemoryEventClient.class)).in(Scopes.SINGLETON);
                    newSetBinder(binder, EventClient.class).addBinding().to(Key.get(NullEventClient.class)).in(Scopes.SINGLETON);
                });
        eventClient = injector.getInstance(EventClient.class);
        memoryEventClient1 = injector.getInstance(InMemoryEventClient.class);
        memoryEventClient2 = injector.getInstance(AnotherInMemoryEventClient.class);
    }

    public static class AnotherInMemoryEventClient
            extends InMemoryEventClient {}
}
