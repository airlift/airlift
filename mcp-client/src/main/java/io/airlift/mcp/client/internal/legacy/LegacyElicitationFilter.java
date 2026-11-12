package io.airlift.mcp.client.internal.legacy;

import io.airlift.log.Logger;
import io.airlift.mcp.client.McpMapper;
import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitRequestUrl;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static java.util.Objects.requireNonNull;

class LegacyElicitationFilter
        implements NotificationConsumer
{
    private static final Logger log = Logger.get(LegacyElicitationFilter.class);

    private final LegacyElicitationHandler handler;
    private final BiConsumer<Object, ElicitResult> elicitResultConsumer;
    private final BiConsumer<Object, JsonRpcErrorDetail> errorConsumer;

    LegacyElicitationFilter(LegacyElicitationHandler handler, BiConsumer<Object, ElicitResult> elicitResultConsumer, BiConsumer<Object, JsonRpcErrorDetail> errorConsumer)
    {
        this.handler = requireNonNull(handler, "handler is null");
        this.elicitResultConsumer = requireNonNull(elicitResultConsumer, "elicitResultConsumer is null");
        this.errorConsumer = requireNonNull(errorConsumer, "errorConsumer is null");
    }

    @Override
    public void accept(Object id, String method, Optional<Object> params)
    {
        if (!method.equals(METHOD_ELICITATION_CREATE)) {
            return;
        }

        if (id == null) {
            // without an id there is no request to answer
            log.debug("Ignoring %s without an id", METHOD_ELICITATION_CREATE);
            return;
        }

        // throwing would end the stream the request arrived on, and the server would still be waiting for an answer
        params.flatMap(LegacyElicitationFilter::readElicitRequest)
                .ifPresentOrElse(
                        elicitRequest -> elicitResultConsumer.accept(id, handler.handleElicitation(elicitRequest)),
                        () -> errorConsumer.accept(id, new JsonRpcErrorDetail(INVALID_PARAMS, "Invalid %s params".formatted(METHOD_ELICITATION_CREATE))));
    }

    private static Optional<ElicitRequest> readElicitRequest(Object params)
    {
        if (!(params instanceof Map<?, ?> requestMap)) {
            log.debug("Unreadable %s params: %s", METHOD_ELICITATION_CREATE, params);
            return Optional.empty();
        }

        Class<? extends ElicitRequest> requestType = requestMap.containsKey("url") ? ElicitRequestUrl.class : ElicitRequestForm.class;
        try {
            return Optional.of(McpMapper.jsonMapper().convertValue(requestMap, requestType));
        }
        catch (IllegalArgumentException e) {
            log.debug(e, "Unreadable %s params", METHOD_ELICITATION_CREATE);
            return Optional.empty();
        }
    }
}
