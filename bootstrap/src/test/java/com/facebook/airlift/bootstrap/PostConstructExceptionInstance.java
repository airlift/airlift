package com.facebook.airlift.bootstrap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class PostConstructExceptionInstance
{
    @PostConstruct
    public void postConstructFailure()
    {
        TestLifeCycleManager.note("postConstructFailure");
        throw new IllegalArgumentException("postConstructFailure");
    }

    @PreDestroy
    public void preDestroyFailureAfterPostConstructFailureOne()
    {
        TestLifeCycleManager.note("preDestroyFailureAfterPostConstructFailureOne");
        throw new IllegalArgumentException("preDestroyFailureAfterPostConstructFailureOne");
    }

    @PreDestroy
    public void preDestroyFailureAfterPostConstructFailureTwo()
    {
        TestLifeCycleManager.note("preDestroyFailureAfterPostConstructFailureTwo");
        throw new IllegalArgumentException("preDestroyFailureAfterPostConstructFailureTwo");
    }
}
