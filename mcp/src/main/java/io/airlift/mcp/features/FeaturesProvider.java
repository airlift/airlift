package io.airlift.mcp.features;

import com.google.inject.Inject;
import io.airlift.mcp.legacy.LegacyFeatures;
import io.airlift.mcp.model.Protocol;

import static java.util.Objects.requireNonNull;

public class FeaturesProvider
{
    private final LegacyFeatures legacyFeatures;

    @Inject
    FeaturesProvider(LegacyFeatures legacyFeatures)
    {
        this.legacyFeatures = requireNonNull(legacyFeatures, "legacyFeatures is null");
    }

    public Features featuresFromProtocol(Protocol protocol)
    {
        return switch (protocol) {
            case PROTOCOL_MCP_2025_11_25, PROTOCOL_MCP_2025_06_18 -> legacyFeatures;
        };
    }
}
