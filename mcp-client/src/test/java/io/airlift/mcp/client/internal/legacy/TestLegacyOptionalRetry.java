package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.McpException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestLegacyOptionalRetry
{
    @Test
    public void testHttpBadRequestTriggersFallback()
    {
        AtomicInteger protocolChanges = new AtomicInteger();
        LegacyOptionalRetry retry = new LegacyOptionalRetry(_ -> protocolChanges.incrementAndGet());

        AtomicInteger calls = new AtomicInteger();
        String result = retry.withRetry(() -> {
            if (calls.incrementAndGet() == 1) {
                throw badRequestException();
            }
            return "from legacy";
        });

        assertThat(result).isEqualTo("from legacy");
        assertThat(protocolChanges).hasValue(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    public void testInvalidRequestErrorTriggersFallback()
    {
        AtomicInteger protocolChanges = new AtomicInteger();
        LegacyOptionalRetry retry = new LegacyOptionalRetry(_ -> protocolChanges.incrementAndGet());

        AtomicInteger calls = new AtomicInteger();
        String result = retry.withRetry(() -> {
            if (calls.incrementAndGet() == 1) {
                throw exception(INVALID_REQUEST, "cannot deserialize");
            }
            return "from legacy";
        });

        assertThat(result).isEqualTo("from legacy");
        assertThat(protocolChanges).hasValue(1);
        assertThat(calls).hasValue(2);
    }

    @Test
    public void testOtherErrorsPropagateWithoutFallbackOrRetry()
    {
        AtomicInteger protocolChanges = new AtomicInteger();
        LegacyOptionalRetry retry = new LegacyOptionalRetry(_ -> protocolChanges.incrementAndGet());

        // a business error on the first call must not downgrade the protocol, and above all must
        // not re-execute the call - it may not be idempotent
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> retry.withRetry(() -> {
            calls.incrementAndGet();
            throw exception(INVALID_PARAMS, "tool failed");
        }))
                .isInstanceOf(McpException.class)
                .hasMessage("tool failed");

        assertThat(protocolChanges).hasValue(0);
        assertThat(calls).hasValue(1);
    }

    @Test
    public void testFirstFailurePropagatingStillValidates()
    {
        AtomicInteger protocolChanges = new AtomicInteger();
        LegacyOptionalRetry retry = new LegacyOptionalRetry(_ -> protocolChanges.incrementAndGet());

        assertThatThrownBy(() -> retry.withRetry(() -> {
            throw exception(INVALID_PARAMS, "tool failed");
        })).isInstanceOf(McpException.class);

        // the protocol question is settled - a later matching failure must not downgrade
        assertThatThrownBy(() -> retry.withRetry(() -> {
            throw badRequestException();
        })).isInstanceOf(McpException.class);

        assertThat(protocolChanges).hasValue(0);
    }

    @Test
    public void testOnlyTheFirstCallCanTriggerFallback()
    {
        AtomicInteger protocolChanges = new AtomicInteger();
        LegacyOptionalRetry retry = new LegacyOptionalRetry(_ -> protocolChanges.incrementAndGet());

        assertThat(retry.withRetry(() -> "validated")).isEqualTo("validated");

        // the current protocol works - later protocol-shaped failures are genuine errors
        assertThatThrownBy(() -> retry.withRetry(() -> {
            throw badRequestException();
        })).isInstanceOf(McpException.class);

        assertThat(protocolChanges).hasValue(0);
    }

    private static McpException badRequestException()
    {
        UnexpectedResponseException cause = new UnexpectedResponseException(
                "Bad Request",
                preparePost().setUri(URI.create("http://localhost:1/mcp")).build(),
                400,
                ImmutableListMultimap.of());
        return new McpException(cause, exception(INVALID_PARAMS, "Bad Request").errorDetail());
    }
}
