package io.airlift.bootstrap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class BarInstance
{
    @PostConstruct
    public void postDependentInstance()
    {
        TestLifeCycleManager.note("postBarInstance");
    }

    @PreDestroy
    public void preDependentInstance()
    {
        TestLifeCycleManager.note("preBarInstance");
    }
}
