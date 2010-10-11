package com.proofpoint.lifecycle;

import com.google.inject.Inject;

public class AnotherInstance
{
    @Inject
    public AnotherInstance(AnInstance anInstance)
    {
    }
}
