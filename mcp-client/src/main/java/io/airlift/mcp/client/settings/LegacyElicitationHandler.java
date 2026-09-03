package io.airlift.mcp.client.settings;

import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitResult;

public interface LegacyElicitationHandler
{
    ElicitResult handleElicitation(ElicitRequest elicitRequest);
}
