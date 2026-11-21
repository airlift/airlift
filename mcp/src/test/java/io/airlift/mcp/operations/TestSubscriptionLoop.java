package io.airlift.mcp.operations;

import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.SubscriptionFilter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TestSubscriptionLoop
{
    @Test
    public void testCatalogsAreReevaluatedForEachRequestContext()
    {
        McpEntities entities = mock(McpEntities.class);
        RequestContextImpl allowed = mock(RequestContextImpl.class);
        RequestContextImpl denied = mock(RequestContextImpl.class);
        SubscriptionFilter filter = new SubscriptionFilter(Optional.empty(), Optional.of(true), Optional.empty(), Optional.empty());
        Prompt prompt = new Prompt("prompt", Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty());
        when(entities.prompts(allowed)).thenReturn(List.of(prompt));
        SubscriptionLoop allowedLoop = loop(entities, allowed, filter);
        SubscriptionLoop deniedLoop = loop(entities, denied, filter);
        allowedLoop.checkForChanges();
        deniedLoop.checkForChanges();
        verifyNoInteractions(allowed, denied);

        // A capability filter can change without a registration change.
        when(entities.prompts(allowed)).thenReturn(List.of());
        allowedLoop.checkForChanges();
        deniedLoop.checkForChanges();
        allowedLoop.checkForChanges();
        verify(allowed).sendMessage(eq(NOTIFICATION_PROMPTS_LIST_CHANGED), any());
        verifyNoMoreInteractions(allowed);
        verifyNoInteractions(denied);
        verify(entities, times(4)).prompts(allowed);
        verify(entities, times(3)).prompts(denied);
    }

    @Test
    public void testResourceChangesAndAbsence()
    {
        McpEntities entities = mock(McpEntities.class);
        RequestContextImpl context = mock(RequestContextImpl.class);
        String uri = "test://resource";
        ReadResourceRequest request = new ReadResourceRequest(uri, Optional.empty());
        SubscriptionFilter filter = new SubscriptionFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of(uri)));
        SubscriptionLoop loop = loop(entities, context, filter);
        loop.checkForChanges();
        verifyNoInteractions(context);

        when(entities.readResourceContents(context, request)).thenReturn(Optional.of(new ReadResourceResult(List.of())));
        loop.checkForChanges();
        when(entities.readResourceContents(context, request)).thenReturn(Optional.of(new ReadResourceResult(List.of(new ResourceContents("resource", uri, "text/plain", "one")))));
        loop.checkForChanges();
        loop.checkForChanges();
        when(entities.readResourceContents(context, request)).thenReturn(Optional.of(new ReadResourceResult(List.of(new ResourceContents("resource", uri, "text/plain", "two")))));
        loop.checkForChanges();
        when(entities.readResourceContents(context, request)).thenReturn(Optional.empty());
        loop.checkForChanges();
        verify(context, times(4)).sendMessage(eq(NOTIFICATION_RESOURCES_UPDATED), any());
        verifyNoMoreInteractions(context);
        verify(entities, times(7)).readResourceContents(context, request);
    }

    private static SubscriptionLoop loop(McpEntities entities, RequestContextImpl context, SubscriptionFilter filter)
    {
        return new SubscriptionLoop(new JsonMapperProvider().get(), "request", entities, context, filter, Duration.ofSeconds(1), Duration.ofMillis(1));
    }
}
