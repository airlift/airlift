package com.proofpoint.lifecycle;

import com.google.inject.Inject;

public class DependentInstanceImpl implements DependentBoundInstance
{
    @Inject
    public DependentInstanceImpl()
    {
    }

    @Override
    public void postDependentBoundInstance()
    {
        TestLifeCycleManager.note("postDependentBoundInstance");
    }

    @Override
    public void preDependentBoundInstance()
    {
        TestLifeCycleManager.note("preDependentBoundInstance");
    }
}
