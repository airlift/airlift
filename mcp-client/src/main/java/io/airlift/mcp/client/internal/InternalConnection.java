package io.airlift.mcp.client.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Comparators;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.log.Logger;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.settings.SettingContainer;
import io.airlift.mcp.client.settings.MetaOnly;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static io.airlift.mcp.client.McpClientSetting.MAX_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpClientSetting.MIN_TASK_SERVICE_PERIOD;
import static io.airlift.mcp.client.McpMapper.requireCallToolResult;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_SERVER_DISCOVER;
import static io.airlift.mcp.model.Constants.METHOD_SUBSCRIPTIONS_LISTEN;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_CANCEL;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_GET;
import static io.airlift.mcp.model.Constants.METHOD_TASKS_UPDATE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static java.util.Objects.requireNonNull;

public class InternalConnection
        implements McpTasksConnection
{
    private static final Logger log = Logger.get(InternalConnection.class);

    private final RequestController requestController;
    private final ThreadFactory threadFactory;
    private final boolean tasksEnabled;

    public static InternalConnection createInternalConnection(McpClient client, URI uri, SettingContainer settingContainer, boolean tasksEnabled)
    {
        RequestController requestController = new RequestController(client.httpClient(), uri, settingContainer, PROTOCOL_MCP_2026_07_28, tasksEnabled);
        return createInternalConnection(requestController);
    }

    public static InternalConnection createInternalConnection(RequestController requestController)
    {
        ThreadFactory threadFactory = virtualThreadsNamed(requestController.uri().toString());
        return new InternalConnection(requestController, threadFactory, requestController.tasksEnabled());
    }

    private InternalConnection(RequestController requestController, ThreadFactory threadFactory, boolean tasksEnabled)
    {
        this.requestController = requireNonNull(requestController, "requestController is null");
        this.threadFactory = requireNonNull(threadFactory, "threadFactory is null");
        this.tasksEnabled = tasksEnabled;
    }

    public SettingContainer settingContainer()
    {
        return requestController.settingContainer();
    }

    public RequestController requestController()
    {
        return requestController;
    }

    @Override
    public URI uri()
    {
        return requestController.uri();
    }

    @Override
    public <V> V setting(McpConnectionSetting<V> setting)
    {
        return requestController.settingContainer().getSettingValue(setting);
    }

    @Override
    public <V> InternalConnection withSetting(McpConnectionSetting<V> setting, V value)
    {
        SettingContainer settingContainer = requestController.settingContainer().with(setting, value);
        return new InternalConnection(requestController.withSettingContainer(settingContainer), threadFactory, tasksEnabled);
    }

    public InternalConnection withRequestController(RequestController requestController)
    {
        return new InternalConnection(requestController, threadFactory, tasksEnabled);
    }

    @Override
    public ListToolsResult listTools(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendCacheableRequest(METHOD_TOOLS_LIST, requestController.applyMeta(request, request::withMeta), ListToolsResult.class);
    }

    @Override
    public ListPromptsResult listPrompts(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendCacheableRequest(METHOD_PROMPT_LIST, requestController.applyMeta(request, request::withMeta), ListPromptsResult.class);
    }

    @Override
    public ListResourcesResult listResources(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendCacheableRequest(METHOD_RESOURCES_LIST, requestController.applyMeta(request, request::withMeta), ListResourcesResult.class);
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendCacheableRequest(METHOD_RESOURCES_TEMPLATES_LIST, requestController.applyMeta(request, request::withMeta), ListResourceTemplatesResult.class);
    }

    @Override
    public CallToolResult callTool(CallToolRequest callToolRequest)
    {
        return requireCallToolResult(callToolOrTask(callToolRequest));
    }

    @Override
    public ToolResult callToolOrTask(CallToolRequest request)
    {
        return sendRequest(METHOD_TOOLS_CALL, requestController.applyMeta(request, request::withMeta), ToolResult.class);
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest request)
    {
        return sendRequest(METHOD_PROMPT_GET, requestController.applyMeta(request, request::withMeta), GetPromptResult.class);
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest request)
    {
        return sendCacheableRequest(METHOD_RESOURCES_READ, requestController.applyMeta(request, request::withMeta), ReadResourceResult.class);
    }

    @Override
    public CompleteResult completeCompletion(CompleteRequest request)
    {
        return sendRequest(METHOD_COMPLETION_COMPLETE, requestController.applyMeta(request, request::withMeta), CompleteResult.class);
    }

    @Override
    public DiscoverResult serverDiscover()
    {
        MetaOnly request = new MetaOnly();
        return sendCacheableRequest(METHOD_SERVER_DISCOVER, requestController.applyMeta(request, request::withMeta), DiscoverResult.class);
    }

    @Override
    public AutoCloseable subscribe(SubscriptionNotifications request)
    {
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<SseStream> sseStreamRef = new AtomicReference<>();
        RequestController localRequestController = requestController.withSseStreamConsumer(sseStream -> {
            sseStreamRef.set(sseStream);
            if (closed.get()) {
                // the subscription was closed before the stream arrived - shut it down on arrival
                sseStream.interrupt();
            }
        });

        Thread thread = threadFactory.newThread(() -> {
            try {
                sendRequest(localRequestController, METHOD_SUBSCRIPTIONS_LISTEN, requestController.applyMeta(request, request::withMeta), MetaOnly.class);
                // the server ends the stream when its event streaming timeout elapses
                log.debug("Subscription listen stream ended: %s", uri());
            }
            catch (Throwable e) {
                if (closed.get()) {
                    log.debug("Subscription cancelled: %s", uri());
                }
                else {
                    log.error(e, "Subscription listen stream failed: %s", uri());
                }
            }
        });
        thread.start();

        return () -> {
            closed.set(true);
            try {
                SseStream sseStream = sseStreamRef.get();
                if (sseStream != null) {
                    sseStream.interrupt();
                }
            }
            finally {
                thread.interrupt();
            }
        };
    }

    @Override
    public ToolResult getTask(GetTaskRequest request)
    {
        return sendRequest(METHOD_TASKS_GET, requestController.applyMeta(request, request::withMeta), ToolResult.class);
    }

    @Override
    public void cancelTask(String taskId)
    {
        GetTaskRequest request = new GetTaskRequest(taskId, Optional.empty());
        sendRequest(METHOD_TASKS_CANCEL, requestController.applyMeta(request, request::withMeta), MetaOnly.class);
    }

    @Override
    public void updateTask(UpdateTaskRequest request)
    {
        sendRequest(METHOD_TASKS_UPDATE, requestController.applyMeta(request, request::withMeta), MetaOnly.class);
    }

    @Override
    public void sleepTask(Task task)
            throws InterruptedException
    {
        Duration minServicePeriod = requestController.settingContainer().getSettingValue(MIN_TASK_SERVICE_PERIOD);
        Duration maxServicePeriod = requestController.settingContainer().getSettingValue(MAX_TASK_SERVICE_PERIOD);

        TimeUnit.NANOSECONDS.sleep(taskSleepPeriod(task, minServicePeriod, maxServicePeriod).toNanos());
    }

    @VisibleForTesting
    public static Duration taskSleepPeriod(Task task, Duration minServicePeriod, Duration maxServicePeriod)
    {
        return task.pollIntervalMs()
                .stream()
                .mapToObj(Duration::ofMillis)
                .map(duration -> Comparators.max(duration, minServicePeriod))
                .map(duration -> Comparators.min(duration, maxServicePeriod))
                .findFirst()
                .orElse(minServicePeriod);
    }

    @Override
    public void close()
    {
        // does nothing now - leaving for future use
    }

    private <T, R extends CacheableResult<R>> R sendCacheableRequest(String method, T request, Class<R> responseType)
    {
        Request.Builder builder = requestController.prepareRequest(method, request);
        builder.setHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2026_07_28.value());
        Request httpRequest = builder.build();

        return requestController.sendCacheableRequest(httpRequest, method, request, responseType);
    }

    private <T, R> R sendRequest(String method, T request, Class<R> responseType)
    {
        return sendRequest(requestController, method, request, responseType);
    }

    private <T, R> R sendRequest(RequestController requestController, String method, T request, Class<R> responseType)
    {
        Request.Builder builder = requestController.prepareRequest(method, request);
        builder.setHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2026_07_28.value());
        return requestController.sendRequest(builder.build(), responseType).result();
    }
}
