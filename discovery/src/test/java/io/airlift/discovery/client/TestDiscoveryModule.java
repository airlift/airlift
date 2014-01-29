package io.airlift.discovery.client;

import com.google.inject.Injector;
import com.google.inject.Key;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.json.JsonModule;
import io.airlift.node.testing.TestingNodeModule;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestDiscoveryModule
        extends AbstractTestDiscoveryModule
{
    protected TestDiscoveryModule()
    {
        super(new DiscoveryModule());
    }

    @Test
    public void testExecutorShutdown()
            throws Exception
    {
        Bootstrap app = new Bootstrap(
                new JsonModule(),
                new TestingNodeModule(),
                new DiscoveryModule());

        Injector injector = app
                .strictConfig()
                .doNotInitializeLogging()
                .initialize();

        ExecutorService executor = injector.getInstance(Key.get(ScheduledExecutorService.class, ForDiscoveryClient.class));
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        assertFalse(executor.isShutdown());
        lifeCycleManager.stop();
        assertTrue(executor.isShutdown());
    }
}
