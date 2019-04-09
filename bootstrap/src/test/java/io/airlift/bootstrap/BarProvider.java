package io.airlift.bootstrap;

import com.google.inject.Provider;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class BarProvider
        implements Provider<BarInstance>
{
    @PostConstruct
    public void postDependentInstance()
    {
        TestLifeCycleManager.note("postBarProvider");
    }

    @PreDestroy
    public void preDependentInstance()
    {
        TestLifeCycleManager.note("preBarProvider");
    }

    @Override
    public BarInstance get()
    {
        return new BarInstance();
    }
}
