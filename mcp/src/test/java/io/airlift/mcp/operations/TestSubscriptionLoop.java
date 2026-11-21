package io.airlift.mcp.operations;

import io.airlift.json.JsonMapperProvider;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.TestingIdentity;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.SubscriptionFilter;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.tasks.TaskExecutor;
import io.airlift.mcp.tasks.memory.MemoryTaskEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.mcp.McpIdentity.Authenticated.authenticated;
import static io.airlift.mcp.model.Constants.ACCEPTS_STREAMING_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.EXTENSION_TASKS;
import static io.airlift.mcp.model.Constants.NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TASKS;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
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
                .filter(message -> message.path("method").asString().equals(NOTIFICATION_TASKS))
                .toList();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().path("params").path("taskId").asString()).isEqualTo(taskId);
        assertThat(notifications.getFirst().path("params").path("status").asString()).isEqualTo("completed");
    }

    @Test
    public void testSubscribedTaskChangeIsNotified()
            throws InterruptedException
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);

        String taskId = engine.createTask(caller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();
        CountDownLatch release = new CountDownLatch(1);
        engine.executeTask(taskId, (_, _, _) -> {
            release.await();
            return new CallToolResult(new TextContent("done"));
        });

        SubscriptionFilter subscriptionFilter = new SubscriptionFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of(taskId)));
        // a streaming timeout far longer than the test, so the change can only be reported by the polling loop
        SubscriptionLoop subscriptionLoop = new SubscriptionLoop(JSON_MAPPER, 1, mock(McpEntities.class), caller, subscriptionFilter, Optional.of(engine), Duration.ofMinutes(1), Duration.ofMillis(10));
        Thread loopThread = Thread.ofVirtual().start(subscriptionLoop::run);
        try {
            // the state sent on subscribing, while the task is still held working
            verify(messageWriter, timeout(5_000)).writeMessage(argThat(message -> isTaskNotification(message, taskId, "working")));

            release.countDown();

            verify(messageWriter, timeout(5_000)).writeMessage(argThat(message -> isTaskNotification(message, taskId, "completed")));
        }
        finally {
            release.countDown();
            loopThread.interrupt();
            loopThread.join(5_000);
        }
        assertThat(loopThread.isAlive()).isFalse();

        List<String> statuses = messages(messageWriter).stream()
                .filter(message -> message.path("method").asString().equals(NOTIFICATION_TASKS))
                .map(message -> message.path("params").path("status").asString())
                .toList();
        assertThat(statuses.getFirst()).isEqualTo("working");
        assertThat(statuses.getLast()).isEqualTo("completed");
    }

    @Test
    public void testTaskChangedBetweenReadsIsNotifiedOnce()
    {
        MessageWriter messageWriter = mock(MessageWriter.class);
        RequestContextImpl caller = requestContext("caller", messageWriter);

        // never run, so cancelling settles it at once
        String taskId = engine.createTask(caller, CALL_TOOL_REQUEST, TASK_TTL, TASK_POLL_INTERVAL).taskId();

        // the task is cancelled right after the loop first reads it working, so every later read sees it cancelled
        AtomicBoolean cancelled = new AtomicBoolean();
        McpTasks tasks = new ForwardingTasks(engine)
        {
            @Override
            public Optional<Task> getTask(String id)
            {
                Optional<Task> task = super.getTask(id);
                if (!cancelled.getAndSet(true)) {
                    engine.cancelTask(id);
                }
                return task;
            }
        };

        SubscriptionFilter subscriptionFilter = new SubscriptionFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of(taskId)));
        // long enough for many polls, each of which must find the cancelled task already reported
        new SubscriptionLoop(JSON_MAPPER, 1, mock(McpEntities.class), caller, subscriptionFilter, Optional.of(tasks), Duration.ofMillis(200), Duration.ofMillis(10))
                .run();

        List<String> statuses = messages(messageWriter).stream()
                .filter(message -> message.path("method").asString().equals(NOTIFICATION_TASKS))
                .map(message -> message.path("params").path("status").asString())
                .toList();
        assertThat(statuses).containsExactly("working", "cancelled");
    }

    private static boolean isTaskNotification(String message, String taskId, String status)
    {
        JsonNode json = readTree(message);
        return json.path("method").asString().equals(NOTIFICATION_TASKS)
                && json.path("params").path("taskId").asString().equals(taskId)
                && json.path("params").path("status").asString().equals(status);
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
                .filter(message -> message.path("method").asString().equals(NOTIFICATION_SUBSCRIPTIONS_ACKNOWLEDGED))
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

    private static class ForwardingTasks
            implements McpTasks
    {
        private final McpTasks delegate;

        private ForwardingTasks(McpTasks delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest, Duration ttl, Duration pollInterval)
        {
            return delegate.createTask(requestContext, callToolRequest, ttl, pollInterval);
        }

        @Override
        public void executeTask(String taskId, TaskExecutor executor)
        {
            delegate.executeTask(taskId, executor);
        }

        @Override
        public boolean validateTaskAccess(McpRequestContext requestContext, String taskId)
        {
            return delegate.validateTaskAccess(requestContext, taskId);
        }

        @Override
        public Optional<Task> getTask(String taskId)
        {
            return delegate.getTask(taskId);
        }

        @Override
        public boolean updateTask(String taskId, Map<String, Object> inputResponses)
        {
            return delegate.updateTask(taskId, inputResponses);
        }

        @Override
        public boolean cancelTask(String taskId)
        {
            return delegate.cancelTask(taskId);
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
