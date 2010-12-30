package com.proofpoint.lifecycle;

import com.google.inject.Inject;

public class InstanceThatUsesInstanceThatRequiresStart {
    @Inject
    public InstanceThatUsesInstanceThatRequiresStart(InstanceThatRequiresStart obj) {
        obj.doSomething();
        TestLifeCycleManager.note("InstanceThatUsesInstanceThatRequiresStart:OK");
    }
}
