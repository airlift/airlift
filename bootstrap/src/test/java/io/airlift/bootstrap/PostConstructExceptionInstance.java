package io.airlift.bootstrap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

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
