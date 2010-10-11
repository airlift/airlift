package com.proofpoint.lifecycle;

import com.google.inject.Inject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class InstanceTwo
{
    @Inject
    public InstanceTwo(DependentBoundInstance dependentInstance)
    {
    }

    @PostConstruct
    public void     postMakeTwo()
    {
        TestLifeCycleManager.note("postMakeTwo");
    }

    @PreDestroy
    public void     preDestroyTwo()
    {
        TestLifeCycleManager.note("preDestroyTwo");
    }
}
