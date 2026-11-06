package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.TestingIdentity;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.tasks.memory.MemoryTaskEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.Constants.ACCEPTS_STREAMING_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TASKS;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestSubscriptionLoop
{
    private static final JsonMapper JSON_MAPPER = new JsonMapperProvider().get();
    private static final Duration TASK_TTL = Duration.ofMinutes(1);
    private static final Duration TASK_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final CallToolRequest CALL_TOOL_REQUEST = new CallToolRequest("test");

    private MemoryTaskEngine engine;

    @BeforeEach
    public void setUp()
    {
        engine = new MemoryTaskEngine();
    }

    @AfterEach
    public void tearDown()
    {
        engine.close();
    }

    @Test
    public void testAcknowledgementListsOnlyAccessibleTasks()
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);
        RequestContextImpl otherCaller = requestContext("other-caller", mock(MessageWriter.class));

        String ownTaskId = engine.createTask(caller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();
        String otherTaskId = engine.createTask(otherCaller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();

        runSubscription(caller, List.of(ownTaskId, otherTaskId, "unknown-task"));

        assertThat(acknowledgedTaskIds(messageWriter)).containsExactly(ownTaskId);
    }

    @Test
    public void testDuplicateTaskIdsAreAcknowledgedOnce()
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);

        String taskId = engine.createTask(caller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();

        runSubscription(caller, List.of(taskId, taskId));

        assertThat(acknowledgedTaskIds(messageWriter)).containsExactly(taskId);
    }

    @Test
    public void testNoAccessibleTasksAcknowledgesAnEmptyList()
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);
        RequestContextImpl otherCaller = requestContext("other-caller", mock(MessageWriter.class));

        String otherTaskId = engine.createTask(otherCaller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();

        runSubscription(caller, List.of(otherTaskId, "unknown-task"));

        // the client asked for tasks, so it is told explicitly that none were accepted
        assertThat(acknowledgedTaskIds(messageWriter)).isEmpty();
    }

    @Test
    public void testSettledTaskIsReportedOnSubscribing()
            throws InterruptedException
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);

        String taskId = engine.createTask(caller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();
        engine.executeTask(taskId, (_, _, _) -> new CallToolResult(new TextContent("done")));
        while (!engine.getTask(taskId).orElseThrow().status().isTerminal()) {
            Thread.sleep(10);
        }

        // the task will never change again, so only the state sent on subscribing can report it
        runSubscription(caller, List.of(taskId));

        List<JsonNode> notifications = messages(messageWriter).stream()
                .filter(message -> message.path("method").asText().equals(NOTIFICATION_TASKS))
                .toList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().path("params").path("taskId").asText()).isEqualTo(taskId);
        assertThat(notifications.getFirst().path("params").path("status").asText()).isEqualTo("completed");
    }

    private void runSubscription(RequestContextImpl requestContext, List<String> taskIds)
    {
        SubscriptionFilter subscriptionFilter = new SubscriptionFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(taskIds));

        // a zero streaming timeout sends the acknowledgement and ends the subscription at once
        new SubscriptionLoop(JSON_MAPPER, 1, mock(McpEntities.class), requestContext, subscriptionFilter, Optional.of(engine), Duration.ZERO, Duration.ofMillis(1))
                .run();
    }

    private static List<String> acknowledgedTaskIds(MessageWriter messageWriter)
    {
        JsonNode acknowledgement = messages(messageWriter).stream()
                .filter(message -> message.path("method").asText().equals(NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED))
                .findFirst()
                .orElseThrow();

        JsonNode taskIds = acknowledgement.path("params").path("notifications").path("taskIds");
        assertThat(taskIds.isArray()).isTrue();
        return JSON_MAPPER.convertValue(taskIds, JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private static List<JsonNode> messages(MessageWriter messageWriter)
    {
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(messageWriter, atLeastOnce()).writeMessage(messages.capture());
        return messages.getAllValues().stream()
                .map(TestSubscriptionLoop::readTree)
                .toList();
    }

    private static JsonNode readTree(String json)
    {
        try {
            return JSON_MAPPER.readTree(json);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private RequestContextImpl requestContext(String identity, MessageWriter messageWriter)
    {
        RequestMetadata requestMetadata = new RequestMetadata(LATEST_PROTOCOL, Optional.empty(), ClientCapabilities.requiredExtension(EXTENSION_TASKS), Optional.empty(), Optional.empty(), Optional.empty());

        // the subscription is a stream, so the connection must accept one
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(ACCEPTS_STREAMING_ATTRIBUTE)).thenReturn(true);

        return new RequestContextImpl(
                request,
                requestMetadata,
                JSON_MAPPER,
                messageWriter,
                authenticated(new TestingIdentity(identity)),
                Optional.of(engine),
                TASK_TTL,
                TASK_POLL_INTERVAL);
    }
}
