package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;

public interface BaseOneWithFooMethod
{
    @PostConstruct
    public void foo();
}
