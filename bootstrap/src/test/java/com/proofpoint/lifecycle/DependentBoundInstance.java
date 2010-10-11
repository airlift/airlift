package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public interface DependentBoundInstance
{
    @PostConstruct
    public void     postDependentBoundInstance();

    @PreDestroy
    public void     preDependentBoundInstance();
}
