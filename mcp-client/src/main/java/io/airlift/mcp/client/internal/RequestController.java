package io.airlift.mcp.client.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.mcp.client.McpConnectionSetting;
import io.airlift.mcp.client.internal.settings.SettingContainer;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.client.settings.RequestCache;
import io.airlift.mcp.client.settings.RequestFilter;
import io.airlift.mcp.client.settings.ResponseFilter;
import io.airlift.mcp.client.settings.SettingMap;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.PaginatedRequest;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.ToolResult;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.HttpStatus.familyForStatusCode;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_NAME;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_VERSION;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.EXPERIMENTAL;
import static io.airlift.mcp.client.McpClientSetting.EXTENSIONS;
import static io.airlift.mcp.client.McpClientSetting.LOGGING_LEVEL;
import static io.airlift.mcp.client.McpClientSetting.REQUEST_CACHE;
import static io.airlift.mcp.client.McpConnectionSetting.EXCEPTION_MAPPER;
import static io.airlift.mcp.client.McpConnectionSetting.PROGRESS_TOKEN;
import static io.airlift.mcp.client.McpMapper.jsonMapper;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_CAPABILITIES;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_INFO;
import static io.airlift.mcp.model.Constants.METADATA_CLIENT_LOG_LEVEL;
import static io.airlift.mcp.model.Constants.METADATA_PROGRESS_TOKEN;
import static io.airlift.mcp.model.Constants.METADATA_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METADATA_TASKS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.model.JsonRpcRequest.buildRequest;
import static java.util.Objects.requireNonNull;

public class RequestController
{
    private static final JsonCodecFactory JSON_CODEC_FACTORY = new JsonCodecFactory(jsonMapper());
    private static final JsonCodec<JsonRpcRequest<Object>> RPC_REQUEST_CODEC = JSON_CODEC_FACTORY.jsonCodec(new TypeToken<>() {});
    private static final JsonCodec<JsonRpcResponse<Object>> RPC_RESPONSE_CODEC = JSON_CODEC_FACTORY.jsonCodec(new TypeToken<>() {});

    private final HttpClient httpClient;
    private final URI uri;
    private final SettingContainer settingContainer;
    private final Protocol protocol;
    private final boolean tasksEnabled;
    private final Consumer<SseStream> sseStreamConsumer;

    public RequestController(HttpClient httpClient, URI uri, SettingContainer settingContainer, Protocol protocol, boolean tasksEnabled)
    {
        this(httpClient, uri, settingContainer, protocol, tasksEnabled, _ -> {});
    }

    private RequestController(HttpClient httpClient, URI uri, SettingContainer settingContainer, Protocol protocol, boolean tasksEnabled, Consumer<SseStream> sseStreamConsumer)
    {
        this.httpClient = requireNonNull(httpClient, "httpClient is null");
        this.uri = requireNonNull(uri, "uri is null");
        this.settingContainer = requireNonNull(settingContainer, "settingContainer is null");
        this.protocol = requireNonNull(protocol, "protocol is null");
        this.tasksEnabled = tasksEnabled;
        this.sseStreamConsumer = requireNonNull(sseStreamConsumer, "sseStreamConsumer is null");
    }

    RequestController withSseStreamConsumer(Consumer<SseStream> sseStreamConsumer)
    {
        return new RequestController(httpClient, uri, settingContainer, protocol, tasksEnabled, sseStreamConsumer);
    }

    public RequestController withSettingContainer(SettingContainer settingContainer)
    {
        return new RequestController(httpClient, uri, settingContainer, protocol, tasksEnabled, sseStreamConsumer);
    }

    public SettingContainer settingContainer()
    {
        return settingContainer;
    }

    public URI uri()
    {
        return uri;
    }

    public <T> Request.Builder prepareNotification(String method, Optional<T> params)
    {
        JsonRpcRequest<T> rpcRequest = params.map(p -> buildNotification(method, p)).orElseGet(() -> buildNotification(method));
        return prepareRequest(rpcRequest);
    }

    public <T> Request.Builder prepareRequest(String method, T params)
    {
        JsonRpcRequest<Object> rpcRequest = buildRequest(UUID.randomUUID().toString(), method, params);
        return prepareRequest(rpcRequest);
    }

    public Request.Builder prepareResponse(Object id, Object params)
    {
        JsonRpcResponse<Object> response = new JsonRpcResponse<>(id, Optional.empty(), Optional.of(params));
        Request.Builder requestBuilder = preparePost()
                .setUri(uri)
                .addHeader(ACCEPT, "application/json, text/event-stream")
                .addHeader(CONTENT_TYPE, "application/json")
                .setBodyGenerator(jsonBodyGenerator(RPC_RESPONSE_CODEC, response));
        RequestFilter requestFilter = settingContainer.getSettingValue(McpConnectionSetting.REQUEST_FILTER);
        return requestFilter.apply(requestBuilder);
    }

    public <T, R extends CacheableResult<R>> R sendCacheableRequest(Request request, String method, T params, Class<R> responseClass)
    {
        Optional<String> cursor = (params instanceof PaginatedRequest paginatedRequest) ? paginatedRequest.cursor() : Optional.empty();
        RequestCache requestCache = settingContainer.getSettingValue(REQUEST_CACHE);
        return requestCache.executeRequest(request, method, params, responseClass, cursor, () -> sendRequest(request, responseClass).result());
    }

    public <R> RequestResult<R> sendRequest(Request request, Class<R> responseClass)
    {
        NotificationConsumer notificationConsumer = settingContainer.getSettingValue(McpConnectionSetting.NOTIFICATION_CONSUMER);
        return sendRequest(request, responseClass, notificationConsumer);
    }

    public <R> RequestResult<R> sendRequest(Request request, Class<R> responseClass, NotificationConsumer notificationConsumer)
    {
        try (var response = httpClient.executeStreaming(request)) {
            if (familyForStatusCode(response.getStatusCode()) != HttpStatus.Family.SUCCESSFUL) {
                throw extractBadRequestError(request, response);
            }

            JsonRpcResponse<Object> rpcResponse;
            String contentType = response.getHeader(CONTENT_TYPE).orElse("");
            if (contentType.startsWith("text/event-stream")) {
                SseStream sseStream = new SseStream(response.getInputStream(), jsonMapper(), notificationConsumer);
                sseStreamConsumer.accept(sseStream);
                rpcResponse = sseStream.read();
            }
            else {
                if (responseClass.equals(Void.class)) {
                    response.getInputStream().readAllBytes();
                    return new RequestResult<>(response, null);
                }

                rpcResponse = RPC_RESPONSE_CODEC.fromJson(response.getInputStream());
            }

            ResponseFilter responseFilter = settingContainer.getSettingValue(McpConnectionSetting.RESPONSE_FILTER);
            rpcResponse = responseFilter.apply(response, rpcResponse);
            if (rpcResponse.error().isPresent()) {
                JsonRpcErrorDetail jsonRpcErrorDetail = rpcResponse.error().orElseThrow();
                throw exception(jsonRpcErrorDetail.code(), jsonRpcErrorDetail.message(), jsonRpcErrorDetail.data());
            }

            Object result = rpcResponse.result().orElseThrow(() -> exception(INVALID_PARAMS, "Result is missing from the RPC response"));
            if (responseClass.equals(ToolResult.class)) {
                return new RequestResult<>(response, responseClass.cast(readToolResult(result)));
            }
            return new RequestResult<>(response, jsonMapper().convertValue(result, responseClass));
        }
        catch (Exception e) {
            throw settingContainer.getSettingValue(EXCEPTION_MAPPER).mapException(request, e);
        }
    }

    public <T extends Meta<?>> T applyMeta(T request, Function<Map<String, Object>, T> mapper)
    {
        SettingMap extensions = settingContainer.getSettingValue(EXTENSIONS);
        if (tasksEnabled) {
            extensions = extensions.with(METADATA_TASKS, ImmutableMap.of());
        }

        SettingMap experimental = settingContainer.getSettingValue(EXPERIMENTAL);

        InitializeRequest.ClientCapabilities clientCapabilities = new InitializeRequest.ClientCapabilities(
                Optional.empty(),
                Optional.empty(),
                settingContainer.getSettingValue(ELICITATION_ENABLED) ? Optional.of(new InitializeRequest.Elicitation()) : Optional.empty(),
                extensions.map().isEmpty() ? Optional.empty() : Optional.of(extensions.map()),
                experimental.map().isEmpty() ? Optional.empty() : Optional.of(experimental.map()));

        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        request.meta().ifPresent(builder::putAll);
        builder.put(METADATA_PROTOCOL_VERSION, protocol.value());
        builder.put(METADATA_CLIENT_CAPABILITIES, clientCapabilities);
        builder.put(METADATA_CLIENT_INFO, new Implementation(settingContainer.getSettingValue(CLIENT_NAME), settingContainer.getSettingValue(CLIENT_VERSION)));
        builder.put(METADATA_CLIENT_LOG_LEVEL, settingContainer.getSettingValue(LOGGING_LEVEL).toJsonValue());
        settingContainer.getSettingValue(PROGRESS_TOKEN).token().ifPresent(token -> builder.put(METADATA_PROGRESS_TOKEN, token));

        return mapper.apply(builder.buildKeepingLast());
    }

    private RuntimeException extractBadRequestError(Request request, Response response)
    {
        if (response.getStatusCode() != HttpStatus.BAD_REQUEST.code()) {
            return new UnexpectedResponseException(request, response);
        }

        try {
            JsonRpcResponse<Object> rpcResponse = RPC_RESPONSE_CODEC.fromJson(response.getInputStream());
            return rpcResponse.error().map(error -> (RuntimeException) exception(error.code(), error.message(), error.data()))
                    .orElseGet(() -> new UnexpectedResponseException(request, response));
        }
        catch (IllegalArgumentException _) {
            return new UnexpectedResponseException(request, response);
        }
        catch (IOException e) {
            UnexpectedResponseException unexpectedResponseException = new UnexpectedResponseException(request, response);
            unexpectedResponseException.addSuppressed(e);
            return unexpectedResponseException;
        }
    }

    private <T> Request.Builder prepareRequest(JsonRpcRequest<T> rpcRequest)
    {
        JsonRpcRequest<Object> mappedRequest = new JsonRpcRequest<>(rpcRequest.jsonrpc(), rpcRequest.id(), rpcRequest.method(), rpcRequest.params().map(p -> p));
        Request.Builder requestBuilder = preparePost()
                .setUri(uri)
                .addHeader(ACCEPT, "application/json, text/event-stream")
                .addHeader(CONTENT_TYPE, "application/json")
                .setBodyGenerator(jsonBodyGenerator(RPC_REQUEST_CODEC, mappedRequest));
        RequestFilter requestFilter = settingContainer.getSettingValue(McpConnectionSetting.REQUEST_FILTER);
        return requestFilter.apply(requestBuilder);
    }

    @SuppressWarnings("rawtypes")
    private static Object readToolResult(Object result)
    {
        if ((result instanceof Map resultMap) && resultMap.containsKey("taskId")) {
            return jsonMapper().convertValue(result, Task.class);
        }
        return jsonMapper().convertValue(result, CallToolResult.class);
    }
}
