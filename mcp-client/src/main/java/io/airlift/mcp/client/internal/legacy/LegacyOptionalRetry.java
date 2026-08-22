package io.airlift.mcp.client.internal.legacy;

import com.google.common.base.Throwables;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.client.McpMapper;
import io.airlift.mcp.model.UnsupportedProtocolVersionError;

import java.util.Optional;
import java.util.function.Consumer;

import static io.airlift.http.client.HttpStatus.BAD_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.UNSUPPORTED_PROTOCOL;
import static java.util.Objects.requireNonNull;

class LegacyOptionalRetry
{
    private volatile boolean validated;
    private final Consumer<Optional<UnsupportedProtocolVersionError>> changeProtocolProc;

    LegacyOptionalRetry(Consumer<Optional<UnsupportedProtocolVersionError>> changeProtocolProc)
    {
        this.changeProtocolProc = requireNonNull(changeProtocolProc, "changeProtocolProc is null");
    }

    interface Handler<R>
    {
        R call();
    }

    <R> R withRetry(Handler<R> proc)
    {
        if (!validated) {
            synchronized (this) {
                if (!validated) {
                    try {
                        return proc.call();
                    }
                    catch (McpException mcpException) {
                        if (mcpException.errorDetail().code() == UNSUPPORTED_PROTOCOL.code()) {
                            Optional<UnsupportedProtocolVersionError> protocolVersionError = mcpException.errorDetail().data()
                                    .flatMap(data -> {
                                        try {
                                            return Optional.of(McpMapper.jsonMapper().convertValue(data, UnsupportedProtocolVersionError.class));
                                        }
                                        catch (IllegalArgumentException _) {
                                            // ignore
                                        }
                                        return Optional.empty();
                                    });
                            changeProtocolProc.accept(protocolVersionError);
                        }
                        else if ((Throwables.getRootCause(mcpException) instanceof UnexpectedResponseException responseException) && (responseException.getStatusCode() == BAD_REQUEST.code())) {
                            changeProtocolProc.accept(Optional.empty());
                        }
                        else if (mcpException.errorDetail().code() == INVALID_REQUEST.code()) {
                            changeProtocolProc.accept(Optional.empty());
                        }
                        else {
                            throw mcpException;
                        }
                    }
                    finally {
                        validated = true;
                    }
                }
            }
        }

        return proc.call();
    }
}
