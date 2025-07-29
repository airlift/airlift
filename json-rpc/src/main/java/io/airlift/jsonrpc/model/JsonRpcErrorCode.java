/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.jsonrpc.model;

import java.util.Optional;

public enum JsonRpcErrorCode
{
    // SDK error codes
    CONNECTION_CLOSED(-32000),
    REQUEST_TIMEOUT(-32001),

    // Standard JSON-RPC error codes
    PARSE_ERROR(-32700),
    INVALID_REQUEST(-32600),
    METHOD_NOT_FOUND(-32601),
    INVALID_PARAMS(-32602),
    INTERNAL_ERROR(-32603);

    private final int code;

    public int code()
    {
        return code;
    }

    public static Optional<JsonRpcErrorCode> fromCode(int code)
    {
        for (JsonRpcErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return Optional.of(errorCode);
            }
        }
        return Optional.empty();
    }

    JsonRpcErrorCode(int code)
    {
        this.code = code;
    }
}
