package io.airlift.jsonrpc.binding;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.io.InputStream;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

record InternalRequest(@Nullable Object id, Optional<String> method, Optional<JsonNode> params, Optional<InputStream> payload)
{
    InternalRequest
    {
        requireNonNull(method, "method is null");
        requireNonNull(params, "params is null");
        requireNonNull(payload, "payload is null");
    }
}
