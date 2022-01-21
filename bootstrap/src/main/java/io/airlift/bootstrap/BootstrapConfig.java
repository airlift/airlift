package io.airlift.bootstrap;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigHidden;

public class BootstrapConfig
{
    private Boolean quiet;

    public Boolean getQuiet()
    {
        return quiet;
    }

    @Config("bootstrap.quiet")
    @ConfigHidden
    public BootstrapConfig setQuiet(Boolean quiet)
    {
        this.quiet = quiet;
        return this;
    }
}
