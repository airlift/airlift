package com.proofpoint.lifecycle;

import javax.annotation.PostConstruct;

public class PostConstructOnly
{
    @PostConstruct
    public void     makeMe()
    {
        TestLifeCycleManager.note("makeMe");
    }
}
