package io.airlift.http.client.jetty;

import io.airlift.http.client.HttpClientModule.JettyIoPoolManager;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.Objects.requireNonNull;

public class QueuedThreadPoolMBeanProvider
        implements Provider<QueuedThreadPoolMBean>
{
    private final JettyIoPoolManager jettyIoPoolManager;

    @Inject
    public QueuedThreadPoolMBeanProvider(JettyIoPoolManager jettyIoPoolManager)
    {
        this.jettyIoPoolManager = requireNonNull(jettyIoPoolManager, "jettyIoPoolManager is null");
    }

    @Override
    public QueuedThreadPoolMBean get()
    {
        return new QueuedThreadPoolMBean((QueuedThreadPool) jettyIoPoolManager.get().getExecutor());
    }
}
