package io.airlift.mcp.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpMultiRoundTripEncoder;

import java.io.UncheckedIOException;
import java.util.Base64;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static java.util.Objects.requireNonNull;

class InternalMultiRoundTripEncoder
        implements McpMultiRoundTripEncoder
{
    private static final Logger log = Logger.get(InternalMultiRoundTripEncoder.class);

    private final JsonMapper jsonMapper;

    @Inject
    InternalMultiRoundTripEncoder(JsonMapper jsonMapper)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
    }

    @Override
    public <T> String encode(Class<T> type, T object)
    {
        try {
            byte[] json = jsonMapper.writeValueAsBytes(object);
            return Base64.getEncoder().encodeToString(json);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> T decode(Class<T> type, String encoded)
    {
        try {
            byte[] json = Base64.getDecoder().decode(encoded);
            return jsonMapper.readValue(json, type);
        }
        catch (Exception e) {
            log.debug(e, "Failed to decode multi-round trip json: %s", encoded);
            throw exception(INVALID_PARAMS, "Failed to request state");
        }
    }
}
