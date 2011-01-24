package com.proofpoint.bootstrap;

import com.google.inject.Inject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class InstanceOne
{
    @Inject
    public InstanceOne(DependentInstance otro)
    {
    }

    @PostConstruct
    public void     postMakeOne()
    {
        TestLifeCycleManager.note("postMakeOne");
    }

    @PreDestroy
    public void     preDestroyOne()
    {
        TestLifeCycleManager.note("preDestroyOne");
    }
}
