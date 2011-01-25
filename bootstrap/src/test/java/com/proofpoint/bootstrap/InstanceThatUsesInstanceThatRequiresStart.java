package com.proofpoint.bootstrap;

import com.google.inject.Inject;

public class InstanceThatUsesInstanceThatRequiresStart
{
    @Inject
    public InstanceThatUsesInstanceThatRequiresStart(InstanceThatRequiresStart obj)
    {
        obj.doSomething();
        TestLifeCycleManager.note("InstanceThatUsesInstanceThatRequiresStart:OK");
    }
}
