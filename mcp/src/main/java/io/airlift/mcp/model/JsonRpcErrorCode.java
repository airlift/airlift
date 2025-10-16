package io.airlift.mcp.model;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.Function.identity;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public enum JsonRpcErrorCode {
    // SDK error codes
    CONNECTION_CLOSED(-32000),
    REQUEST_TIMEOUT(-32001),

    // Standard JSON-RPC error codes
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603);

    private static final Map<Integer, JsonRpcErrorCode> CODE_LOOKUP =
            Stream.of(values()).collect(toImmutableMap(JsonRpcErrorCode::code, identity()));

    private final int code;

    public int code() {
        return code;
    }

    public static Optional<JsonRpcErrorCode> fromCode(int code) {
        return Optional.ofNullable(CODE_LOOKUP.get(code));
    }

    JsonRpcErrorCode(int code) {
        this.code = code;
    }
}
