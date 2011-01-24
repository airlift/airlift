package com.proofpoint.bootstrap;

import javax.annotation.PostConstruct;

public class PostConstructOnly
{
    @PostConstruct
    public void     makeMe()
    {
        TestLifeCycleManager.note("makeMe");
    }
}
