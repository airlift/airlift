package com.proofpoint.bootstrap;

import javax.annotation.PostConstruct;

public interface BaseOneWithFooMethod
{
    @PostConstruct
    public void foo();
}
