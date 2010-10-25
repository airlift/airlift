package com.proofpoint.lifecycle;

public class FooTestInstance implements BaseOneWithFooMethod, BaseTwoWithFooMethod
{
    @Override
    public void foo()
    {
        TestLifeCycleManager.note("foo");
    }
}
