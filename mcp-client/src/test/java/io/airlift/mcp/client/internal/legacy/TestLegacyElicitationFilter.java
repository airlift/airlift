package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.ElicitRequest;
import io.airlift.mcp.model.ElicitRequestForm;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.ElicitResult.Action.ACCEPT;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestLegacyElicitationFilter
{
    private static final ElicitResult RESULT = new ElicitResult(ACCEPT, Optional.empty(), Optional.empty());

    private final List<ElicitRequest> handled = new ArrayList<>();
    private final Map<Object, ElicitResult> results = new HashMap<>();
    private final Map<Object, JsonRpcErrorDetail> errors = new HashMap<>();
    private final LegacyElicitationFilter filter = new LegacyElicitationFilter(
            request -> {
                handled.add(request);
                return RESULT;
            },
            results::put,
            errors::put);

    @Test
    public void testElicitationIsAnswered()
    {
        filter.accept(1, METHOD_ELICITATION_CREATE, Optional.of(ImmutableMap.of("message", "Proceed?", "requestedSchema", ImmutableMap.of("type", "object"))));

        assertThat(handled).singleElement().isInstanceOfSatisfying(ElicitRequestForm.class, request -> assertThat(request.message()).isEqualTo("Proceed?"));
        assertThat(results).containsExactly(Map.entry(1, RESULT));
        assertThat(errors).isEmpty();
    }

    @Test
    public void testUnreadableElicitationIsAnsweredWithAnError()
    {
        // the server is waiting for an answer, so an unreadable request is answered rather than thrown out of the stream
        filter.accept(1, METHOD_ELICITATION_CREATE, Optional.empty());
        filter.accept(2, METHOD_ELICITATION_CREATE, Optional.of("not an object"));
        filter.accept(3, METHOD_ELICITATION_CREATE, Optional.of(ImmutableMap.of("requestedSchema", ImmutableMap.of("type", "object"))));

        assertThat(handled).isEmpty();
        assertThat(results).isEmpty();
        assertThat(errors).containsOnlyKeys(1, 2, 3);
        assertThat(errors.values()).allSatisfy(error -> assertThat(error.code()).isEqualTo(INVALID_PARAMS.code()));
    }

    @Test
    public void testElicitationWithoutAnIdIsIgnored()
    {
        filter.accept(null, METHOD_ELICITATION_CREATE, Optional.empty());

        assertThat(handled).isEmpty();
        assertThat(results).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test
    public void testOtherMethodsAreIgnored()
    {
        filter.accept(null, NOTIFICATION_PROGRESS, Optional.of(ImmutableMap.of("progressToken", "token", "progress", 1)));

        assertThat(handled).isEmpty();
        assertThat(results).isEmpty();
        assertThat(errors).isEmpty();
    }
}
