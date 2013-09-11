package io.airlift.bootstrap;

import com.google.inject.Inject;
import com.google.inject.Injector;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractLifeCycleChecker
{
    private final AtomicInteger count = new AtomicInteger(0);
    private final AtomicInteger preDestroyCalled = new AtomicInteger();
    private final AtomicInteger postConstructCalled = new AtomicInteger();
    private final AtomicInteger injectCalled = new AtomicInteger();

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();


    @PostConstruct
    public final void checkStart()
    {
        if (started.compareAndSet(false, true)) {
            checkState(postConstructCalled.get() == 0, "already injected!");
            postConstructCalled.set(count.incrementAndGet());
        }
    }

    @PreDestroy
    public final void checkStop()
    {
        if (stopped.compareAndSet(false, true)) {
            checkState(preDestroyCalled.get() == 0, "already injected!");
            preDestroyCalled.set(count.incrementAndGet());
        }
    }

    @Inject
    final void checkInject(Injector injector)
    {
        checkState(injectCalled.get() == 0, "already injected!");
        injectCalled.set(count.incrementAndGet());
    }

    public int getPreDestroyCalled()
    {
        return preDestroyCalled.get();
    }

    public int getPostConstructCalled()
    {
        return postConstructCalled.get();
    }

    public int getInjectCalled()
    {
        return injectCalled.get();
    }

    public int getCount()
    {
        return count.get();
    }
}
