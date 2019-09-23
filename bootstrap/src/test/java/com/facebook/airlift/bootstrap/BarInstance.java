package com.facebook.airlift.bootstrap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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
