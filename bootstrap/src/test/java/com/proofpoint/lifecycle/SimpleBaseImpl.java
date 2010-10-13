package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public class SimpleBaseImpl implements SimpleBase
{
    @Override
    public void foo()
    {
    }

    @Override
    public void bar()
    {
    }

    @PostConstruct
    public void     postSimpleBaseImpl()
    {
        TestLifeCycleManager.note("postSimpleBaseImpl");
    }

    @PreDestroy
    public void     preSimpleBaseImpl()
    {
        TestLifeCycleManager.note("preSimpleBaseImpl");
    }
}
