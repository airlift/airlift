package com.proofpoint.bootstrap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class IllegalInstance
{
    @PostConstruct
    public void createWithArgs(String foo)
    {
        TestLifeCycleManager.note("createWithArgs");
    }

    @PreDestroy
    public void destroyWithArgs(String foo)
    {
        TestLifeCycleManager.note("destroyWithArgs");
    }
}
