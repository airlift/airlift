package com.proofpoint.lifecycle;

import javax.annotation.PreDestroy;

public class PreDestroyOnly
{
    @PreDestroy
    public void     unmakeMe()
    {
        TestLifeCycleManager.note("unmakeMe");
    }
}
