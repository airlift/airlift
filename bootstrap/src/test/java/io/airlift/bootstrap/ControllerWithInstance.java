package io.airlift.bootstrap;

import com.google.inject.Inject;

import static java.util.Objects.requireNonNull;

public class ControllerWithInstance
{
    // InstanceWithLifecycle must be injected into another object
    // to exhibit the double start issue with optional binding
    @Inject
    public ControllerWithInstance(InstanceWithLifecycle instance)
    {
        requireNonNull(instance, "instance is null");
    }
}
