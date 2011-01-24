package com.proofpoint.bootstrap;

import com.google.inject.Inject;

public class AnotherInstance
{
    @Inject
    public AnotherInstance(AnInstance anInstance)
    {
    }
}
