package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record Implementation(String name, String version) {
    public Implementation {
        requireNonNull(name, "name is null");
        requireNonNull(version, "version is null");
    }
}
