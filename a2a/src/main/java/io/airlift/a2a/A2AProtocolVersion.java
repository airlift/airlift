package io.airlift.a2a;

import static java.util.Objects.requireNonNull;

public enum A2AProtocolVersion
{
    V_0_3_0("0.3.0"),
    V_1_0_0("1.0.0");

    private final String version;

    public String version()
    {
        return version;
    }

    A2AProtocolVersion(String version)
    {
        this.version = requireNonNull(version, "version is null");
    }
}
