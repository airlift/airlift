package com.proofpoint.bootstrap;

import javax.annotation.PostConstruct;

public interface BaseTwoWithFooMethod
{
    @PostConstruct
    public void foo();
}
