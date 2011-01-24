package com.proofpoint.bootstrap;

public class FooTestInstance implements BaseOneWithFooMethod, BaseTwoWithFooMethod
{
    @Override
    public void foo()
    {
        TestLifeCycleManager.note("foo");
    }
}
