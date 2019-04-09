package io.airlift.bootstrap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Provider;

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
