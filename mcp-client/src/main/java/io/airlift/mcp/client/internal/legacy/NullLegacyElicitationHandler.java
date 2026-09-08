package io.airlift.mcp.client.internal.legacy;

import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitResult;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;

public class NullLegacyElicitationHandler
        implements LegacyElicitationHandler
{
    @Override
    public ElicitResult handleElicitation(ElicitRequest elicitRequest)
    {
        throw exception(INVALID_PARAMS, "Elicitation is not enabled");
    }
}
