package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.Request;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.McpTasksConnection;
import io.airlift.mcp.client.internal.RequestController;
import io.airlift.mcp.client.internal.RequestResult;
import io.airlift.mcp.client.internal.settings.Setting;
import io.airlift.mcp.client.internal.settings.SettingContainer;
import io.airlift.mcp.client.settings.LegacyElicitationHandler;
import io.airlift.mcp.client.settings.MetaOnly;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.client.settings.SettingMap;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.InitializeRequest.Elicitation;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;
import io.airlift.mcp.model.UpdateTaskRequest;

import java.net.URI;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ThreadFactory;

import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_NAME;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_VERSION;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.EXPERIMENTAL;
import static io.airlift.mcp.client.McpClientSetting.EXTENSIONS;
import static io.airlift.mcp.client.McpClientSetting.LOGGING_LEVEL;
import static io.airlift.mcp.client.McpConnectionSetting.LEGACY_ELICITATION_HANDLER;
import static io.airlift.mcp.client.McpConnectionSetting.NOTIFICATION_CONSUMER;
import static io.airlift.mcp.client.McpMapper.requireCallToolResult;
import static io.airlift.mcp.model.Constants.HEADER_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_LOGGING_SET_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_SUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_UNSUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.Objects.requireNonNull;

public class LegacyConnection
        implements McpTasksConnection
{
    private static final Setting<InitializeResult> INITIALIZE_RESULT = new Setting<>(InitializeResult.class) {};
    private static final Setting<LegacySessionId> LEGACY_SESSION_ID = new Setting<>(LegacySessionId.class) {};

    private final RequestController requestController;
    private final ThreadFactory threadFactory;

    public static LegacyConnection createLegacyConnection(McpClient client, URI uri, SettingContainer settingContainer)
    {
        RequestController requestController = new RequestController(client.httpClient(), uri, settingContainer, PROTOCOL_MCP_2025_11_25, false);
        return createLegacyConnection(requestController.withSettingContainer(settingContainer));
    }

    public static LegacyConnection createLegacyConnection(RequestController requestController)
    {
        SettingContainer settingContainer = initialize(requestController);
        return new LegacyConnection(requestController.withSettingContainer(settingContainer), virtualThreadsNamed(requestController.uri().toString()));
    }

    private LegacyConnection(RequestController requestController, ThreadFactory threadFactory)
    {
        this.requestController = requireNonNull(requestController, "requestController is null");

        this.threadFactory = requireNonNull(threadFactory, "threadFactory is null");
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
    public <V> LegacyConnection withSetting(McpConnectionSetting<V> setting, V value)
    {
        SettingContainer settingContainer = requestController.settingContainer().with(setting, value);
        return new LegacyConnection(requestController.withSettingContainer(settingContainer), threadFactory);
    }

    LegacyConnection withMergedSettingContainer(SettingContainer settingContainer)
    {
        SettingContainer newSettingContainer = requestController.settingContainer().merge(settingContainer);
        return new LegacyConnection(requestController.withSettingContainer(newSettingContainer), threadFactory);
    }

    @Override
    public DiscoverResult serverDiscover()
    {
        InitializeResult initializeResult = requestController.settingContainer().getSettingValue(INITIALIZE_RESULT);

        return new DiscoverResult(
                Optional.of(COMPLETE),
                ImmutableList.of(PROTOCOL_MCP_2025_11_25.value()),
                initializeResult.capabilities(),
                initializeResult.instructions(),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Override
    public ListToolsResult listTools(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendRequest(METHOD_TOOLS_LIST, requestController.applyMeta(request, request::withMeta), ListToolsResult.class);
    }

    @Override
    public ListPromptsResult listPrompts(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendRequest(METHOD_PROMPT_LIST, requestController.applyMeta(request, request::withMeta), ListPromptsResult.class);
    }

    @Override
    public ListResourcesResult listResources(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendRequest(METHOD_RESOURCES_LIST, requestController.applyMeta(request, request::withMeta), ListResourcesResult.class);
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor)
    {
        ListRequest request = new ListRequest(cursor, Optional.empty());
        return sendRequest(METHOD_RESOURCES_TEMPLATES_LIST, requestController.applyMeta(request, request::withMeta), ListResourceTemplatesResult.class);
    }

    @Override
    public CallToolResult callTool(CallToolRequest request)
    {
        return requireCallToolResult(sendRequest(METHOD_TOOLS_CALL, requestController.applyMeta(request, request::withMeta), ToolResult.class));
    }

    @Override
    public ToolResult callToolOrTask(CallToolRequest request)
    {
        return sendRequest(METHOD_TOOLS_CALL, requestController.applyMeta(request, request::withMeta), ToolResult.class);
    }

    @Override
    public ToolResult getTask(GetTaskRequest request)
    {
        throw new IllegalStateException("Tasks are not support at this protocol level");
    }

    @Override
    public void cancelTask(String taskId)
    {
        throw new IllegalStateException("Tasks are not support at this protocol level");
    }

    @Override
    public void updateTask(UpdateTaskRequest request)
    {
        throw new IllegalStateException("Tasks are not support at this protocol level");
    }

    @Override
    public void sleepTask(Task task)
    {
        throw new IllegalStateException("Tasks are not support at this protocol level");
    }

    @Override
    public void close()
    {
        // NOP
    }

    @Override
    public GetPromptResult getPrompt(GetPromptRequest request)
    {
        return sendRequest(METHOD_PROMPT_GET, requestController.applyMeta(request, request::withMeta), GetPromptResult.class);
    }

    @Override
    public ReadResourceResult readResource(ReadResourceRequest request)
    {
        return sendRequest(METHOD_RESOURCES_READ, requestController.applyMeta(request, request::withMeta), ReadResourceResult.class);
    }

    @Override
    public CompleteResult completeCompletion(CompleteRequest request)
    {
        return sendRequest(METHOD_COMPLETION_COMPLETE, requestController.applyMeta(request, request::withMeta), CompleteResult.class);
    }

    @Override
    public AutoCloseable subscribe(SubscriptionNotifications subscriptionNotifications)
    {
        subscriptionNotifications.notifications().resourceSubscriptions()
                .ifPresent(resourceSubscriptions -> resourceSubscriptions
                        .forEach(resourceSubscription -> {
                            SubscribeRequest request = new SubscribeRequest(resourceSubscription, Optional.empty());
                            sendRequest(METHOD_RESOURCES_SUBSCRIBE, requestController.applyMeta(request, request::withMeta), MetaOnly.class);
                        }));

        Request.Builder builder = prepareGet()
                .setUri(requestController.uri())
                .addHeader(ACCEPT, "application/json, text/event-stream")
                .addHeader(CONTENT_TYPE, "application/json");
        addHeaders(builder);

        Thread thread = threadFactory.newThread(() -> requestController.sendRequest(builder.build(), Void.class, buildAppliedNotificationConsumer()));
        thread.start();

        return () -> subscriptionNotifications.notifications().resourceSubscriptions()
                .ifPresent(resourceSubscriptions -> resourceSubscriptions
                        .forEach(resourceSubscription -> {
                            SubscribeRequest request = new SubscribeRequest(resourceSubscription, Optional.empty());
                            sendRequest(METHOD_RESOURCES_UNSUBSCRIBE, requestController.applyMeta(request, request::withMeta), MetaOnly.class);
                        }));
    }

    private <T, R> R sendRequest(String method, T request, Class<R> resultClass)
    {
        Request.Builder builder = requestController.prepareRequest(method, request);

        addHeaders(builder);

        return requestController.sendRequest(builder.build(), resultClass, buildAppliedNotificationConsumer()).result();
    }

    private NotificationConsumer buildAppliedNotificationConsumer()
    {
        SettingContainer settingContainer = requestController.settingContainer();
        LegacyElicitationHandler legacyElicitationHandler = settingContainer.getSettingValue(LEGACY_ELICITATION_HANDLER);
        LegacyElicitationFilter legacyElicitationFilter = new LegacyElicitationFilter(legacyElicitationHandler, this::sendResponse);
        NotificationConsumer notificationConsumer = settingContainer.getSettingValue(NOTIFICATION_CONSUMER);
        return legacyElicitationFilter.andThen(notificationConsumer);
    }

    private void addHeaders(Request.Builder builder)
    {
        Optional<String> sessionId = requestController.settingContainer().getSettingValue(LEGACY_SESSION_ID).sessionId();
        addHeaders(builder, sessionId);
    }

    private static void addHeaders(Request.Builder builder, Optional<String> maybeSessionId)
    {
        maybeSessionId.ifPresent(sessionId -> builder.setHeader(HeaderName.of(HEADER_SESSION_ID), sessionId));
        builder.setHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2025_11_25.value());
    }

    private static SettingContainer initialize(RequestController requestController)
    {
        SettingContainer settingContainer = requestController.settingContainer();

        SettingMap experimental = settingContainer.getSettingValue(EXPERIMENTAL);
        SettingMap extensions = settingContainer.getSettingValue(EXTENSIONS);

        ClientCapabilities clientCapabilities = new ClientCapabilities(
                Optional.empty(),
                Optional.empty(),
                settingContainer.getSettingValue(ELICITATION_ENABLED) ? Optional.of(new Elicitation()) : Optional.empty(),
                extensions.map().isEmpty() ? Optional.empty() : Optional.of(extensions.map()),
                experimental.map().isEmpty() ? Optional.empty() : Optional.of(experimental.map()));

        Implementation clientInfo = new Implementation(settingContainer.getSettingValue(CLIENT_NAME), settingContainer.getSettingValue(CLIENT_VERSION));

        InitializeRequest initializeRequest = new InitializeRequest(PROTOCOL_MCP_2025_11_25.value(), clientCapabilities, clientInfo, Optional.empty());
        Request.Builder builder = requestController.prepareRequest(METHOD_INITIALIZE, requestController.applyMeta(initializeRequest, initializeRequest::withMeta));
        builder.setHeader(HeaderName.of(HEADER_PROTOCOL_VERSION), PROTOCOL_MCP_2025_11_25.value());
        RequestResult<InitializeResult> requestResult = requestController.sendRequest(builder.build(), InitializeResult.class);
        InitializeResult initializeResult = requestResult.result();

        Optional<String> sessionId = requestResult.response().getHeader(HeaderName.of(HEADER_SESSION_ID));

        MetaOnly metaOnly = new MetaOnly(Optional.empty());
        builder = requestController.prepareNotification(NOTIFICATION_INITIALIZED, Optional.of(requestController.applyMeta(metaOnly, metaOnly::withMeta)));
        addHeaders(builder, sessionId);
        requestController.sendRequest(builder.build(), Void.class);

        if (initializeResult.capabilities().logging().isPresent()) {
            LoggingLevel loggingLevel = settingContainer.getSettingValue(LOGGING_LEVEL);
            sendLoggingLevel(requestController, loggingLevel, sessionId);
        }

        return settingContainer.with(INITIALIZE_RESULT, initializeResult)
                .with(LEGACY_SESSION_ID, new LegacySessionId(sessionId));
    }

    private static void sendLoggingLevel(RequestController requestController, LoggingLevel loggingLevel, Optional<String> maybeSessionId)
    {
        SetLevelRequest setLevelRequest = new SetLevelRequest(loggingLevel, Optional.empty());
        Request.Builder builder = requestController.prepareRequest(METHOD_LOGGING_SET_LEVEL, Optional.of(requestController.applyMeta(setLevelRequest, setLevelRequest::withMeta)));
        addHeaders(builder, maybeSessionId);
        requestController.sendRequest(builder.build(), Void.class);
    }

    private void sendResponse(Object id, ElicitResult result)
    {
        Request.Builder responseBuilder = requestController.prepareResponse(id, result);
        addHeaders(responseBuilder);
        requestController.sendRequest(responseBuilder.build(), Void.class);
    }
}
