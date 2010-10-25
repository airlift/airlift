package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;

public interface BaseTwoWithFooMethod
{
    @PostConstruct
    public void foo();
}
