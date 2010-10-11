package com.proofpoint.lifecycle;

import com.google.inject.Inject;

public class AnInstance
{
    @Inject
    public AnInstance(DependentInstance dependentInstance)
    {
    }
}
