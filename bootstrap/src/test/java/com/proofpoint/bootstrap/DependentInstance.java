package com.proofpoint.bootstrap;

import com.google.inject.Inject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class DependentInstance
{
    @Inject
    public DependentInstance()
    {
    }

    @PostConstruct
    public void postDependentInstance()
    {
        TestLifeCycleManager.note("postDependentInstance");
    }

    @PreDestroy
    public void preDependentInstance()
    {
        TestLifeCycleManager.note("preDependentInstance");
    }
}
