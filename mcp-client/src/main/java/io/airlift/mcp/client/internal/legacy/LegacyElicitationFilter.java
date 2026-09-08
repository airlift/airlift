package io.airlift.mcp.client.internal.legacy;

import io.airlift.mcp.client.McpMapper;
import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitRequestUrl;
import io.airlift.mcp.model.ElicitResult;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static java.util.Objects.requireNonNull;

class LegacyElicitationFilter
        implements NotificationConsumer
{
    private final LegacyElicitationHandler handler;
    private final BiConsumer<Object, ElicitResult> elicitResultConsumer;

    LegacyElicitationFilter(LegacyElicitationHandler handler, BiConsumer<Object, ElicitResult> elicitResultConsumer)
    {
        this.handler = requireNonNull(handler, "handler is null");
        this.elicitResultConsumer = requireNonNull(elicitResultConsumer, "elicitResultConsumer is null");
    }

    @Override
    public void accept(Object id, String method, Optional<Object> params)
    {
        if (method.equals(METHOD_ELICITATION_CREATE)) {
            Object request = params.orElseThrow(() -> new IllegalArgumentException("Missing params")); // TODO
            if (request instanceof Map<?, ?> requestMap) {
                handleElicitation(id, requestMap);
            }
        }
    }

    private void handleElicitation(Object id, Map<?, ?> requestMap)
    {
        ElicitRequest elicitRequest;
        if (requestMap.containsKey("url")) {
            elicitRequest = McpMapper.jsonMapper().convertValue(requestMap, ElicitRequestUrl.class);
        }
        else {
            elicitRequest = McpMapper.jsonMapper().convertValue(requestMap, ElicitRequestForm.class);
        }

        ElicitResult elicitResult = handler.handleElicitation(elicitRequest);
        elicitResultConsumer.accept(id, elicitResult);
    }
}
