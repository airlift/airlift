package io.airlift.mcp.features;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.legacy.LegacyCancellationController;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionController;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedReader;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.legacy.sessions.LegacySessionController.optionalSessionId;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.PROTOCOL;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.cancellationKey;
import static io.airlift.mcp.model.Constants.META_PROTOCOL_VERSION;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_LOGGING_SET_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_SUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_UNSUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_ROOTS_LIST_CHANGED;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.JsonRpcErrorCode.PARSE_ERROR;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class RpcMessageParser
{
    private static final FeatureCallReference<Object, ?> NOP_FEATURE_CALL = (_, _, _) -> ImmutableMap.of();
    private static final Object NOP_PARAMS = new Object();

    private static final JsonRpcMessage DUMMY_MESSAGE = JsonRpcRequest.buildNotification(NOTIFICATION_CANCELLED);

    private final JsonMapper jsonMapper;
    private final LegacySessionController sessionController;
    private final FeaturesProvider featuresProvider;
    private final LegacyCancellationController cancellationController;
    private final boolean httpGetEventsEnabled;

    @Inject
    public RpcMessageParser(JsonMapper jsonMapper, LegacySessionController sessionController, FeaturesProvider featuresProvider, LegacyCancellationController cancellationController, McpConfig mcpConfig)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.featuresProvider = requireNonNull(featuresProvider, "featuresProvider is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");

        httpGetEventsEnabled = mcpConfig.isHttpGetEventsEnabled();
    }

    public Optional<LegacySession> parseLegacySession(HttpServletRequest request)
    {
        return optionalSessionId(request)
                .flatMap(sessionController::session);
    }

    public ParsedRpcMessage parse(HttpServletRequest request)
    {
        return switch (request.getMethod().toUpperCase(ROOT)) {
            case POST -> handleRpcPost(request);
            case DELETE -> buildFromRpcMessage(DUMMY_MESSAGE, protocolFromRequest(request), NOP_PARAMS, wrapVoid((features, requestContext, _) -> features.acceptSessionDelete(requestContext)));
            case GET -> buildFromRpcMessage(DUMMY_MESSAGE, protocolFromRequest(request), NOP_PARAMS, wrapVoid((features, requestContext, _) -> features.handleEventStreaming(requestContext)));
            default -> throw new IllegalArgumentException("Unknown HTTP method: " + request.getMethod());
        };
    }

    public ParsedRpcMessage handleRpcPost(HttpServletRequest request)
    {
        try (BufferedReader reader = request.getReader()) {
            String body = reader.lines().collect(joining("\n"));
            JsonRpcMessage message = deserializeJsonRpcMessage(body);

            return switch (message) {
                case JsonRpcRequest<?> rpcRequest when rpcRequest.isNotification() -> parseMcpNotification(request, rpcRequest);
                case JsonRpcRequest<?> rpcRequest -> parseMcpRequest(request, rpcRequest);
                case JsonRpcResponse<?> rpcResponse -> buildFromRpcMessage(rpcResponse, protocolFromRequest(request), NOP_PARAMS, wrapVoid((features, requestContext, _) -> features.acceptResponse(requestContext, rpcResponse)));
            };
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T convertParams(JsonRpcRequest<?> rpcRequest, Class<T> clazz)
    {
        Object value = rpcRequest.params().map(v -> (Object) v).orElseGet(ImmutableMap::of);
        return jsonMapper.convertValue(value, clazz);
    }

    private Protocol protocolFromRequest(HttpServletRequest request)
    {
        return parseLegacySession(request)
                .flatMap(session -> session.getValue(PROTOCOL))
                .orElse(PROTOCOL_MCP_2025_11_25);
    }

    private ParsedRpcMessage parseMcpNotification(HttpServletRequest request, JsonRpcRequest<?> rpcRequest)
    {
        return switch (rpcRequest.method()) {
            case NOTIFICATION_INITIALIZED -> buildFromRpcRequest(request, rpcRequest, Object.class, NOP_FEATURE_CALL);
            case NOTIFICATION_CANCELLED -> buildFromRpcRequest(request, rpcRequest, CancelledNotification.class, wrapVoid(Features::acceptCancellation));
            case NOTIFICATION_ROOTS_LIST_CHANGED ->
                    buildFromRpcRequest(request, rpcRequest, Object.class, wrapVoid((features, requestContext, _) -> features.acceptRootsChanged(requestContext)));
            default -> throw exception(METHOD_NOT_FOUND, "Unknown notification: " + rpcRequest.method());
        };
    }

    private ParsedRpcMessage parseMcpRequest(HttpServletRequest request, JsonRpcRequest<?> rpcRequest)
    {
        return switch (rpcRequest.method()) {
            case METHOD_INITIALIZE -> buildFromRpcRequest(request, rpcRequest, InitializeRequest.class, Features::initialize);
            case METHOD_TOOLS_LIST -> buildFromRpcRequest(request, rpcRequest, ListRequest.class, Features::listTools);
            case METHOD_TOOLS_CALL -> buildFromRpcRequest(request, rpcRequest, CallToolRequest.class, Features::callTool);
            case METHOD_PROMPT_LIST -> buildFromRpcRequest(request, rpcRequest, ListRequest.class, Features::listPrompts);
            case METHOD_PROMPT_GET -> buildFromRpcRequest(request, rpcRequest, GetPromptRequest.class, Features::getPrompt);
            case METHOD_RESOURCES_LIST -> buildFromRpcRequest(request, rpcRequest, ListRequest.class, Features::listResources);
            case METHOD_RESOURCES_TEMPLATES_LIST -> buildFromRpcRequest(request, rpcRequest, ListRequest.class, Features::listResourceTemplates);
            case METHOD_RESOURCES_READ -> buildFromRpcRequest(request, rpcRequest, ReadResourceRequest.class, Features::readResources);
            case METHOD_COMPLETION_COMPLETE -> buildFromRpcRequest(request, rpcRequest, CompleteRequest.class, Features::completionComplete);
            case METHOD_LOGGING_SET_LEVEL -> buildFromRpcRequest(request, rpcRequest, SetLevelRequest.class, wrapVoid(Features::setLoggingLevel));
            case METHOD_RESOURCES_SUBSCRIBE -> buildFromRpcRequest(request, rpcRequest, SubscribeRequest.class, wrapVoid(Features::resourcesSubscribe));
            case METHOD_RESOURCES_UNSUBSCRIBE -> buildFromRpcRequest(request, rpcRequest, SubscribeRequest.class, wrapVoid(Features::resourcesUnsubscribe));
            case METHOD_PING -> buildFromRpcRequest(request, rpcRequest, Object.class, NOP_FEATURE_CALL);
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + rpcRequest.method());
        };
    }

    private interface FeatureCallReference<T, R>
    {
        R call(Features features, McpRequestContext requestContext, T params);
    }

    private interface VoidFeatureCallReference<T>
            extends FeatureCallReference<T, Object>
    {
        default Object call(Features features, McpRequestContext requestContext, T params)
        {
            callVoid(features, requestContext, params);
            return ImmutableMap.of();
        }

        void callVoid(Features features, McpRequestContext requestContext, T params);
    }

    private static <T> FeatureCallReference<T, Object> wrapVoid(VoidFeatureCallReference<T> call)
    {
        return call;
    }

    private <T, R> ParsedRpcMessage buildFromRpcRequest(HttpServletRequest request, JsonRpcRequest<?> rpcRequest, Class<T> clazz, FeatureCallReference<T, R> featureCall)
    {
        T params = convertParams(rpcRequest, clazz);

        Protocol protocol = switch (params) {
            case InitializeRequest initializeRequest -> Protocol.of(initializeRequest.protocolVersion()).orElse(PROTOCOL_MCP_2025_11_25);
            case Meta meta -> meta.meta().flatMap(m -> Optional.ofNullable(m.get(META_PROTOCOL_VERSION)).map(Object::toString))
                    .flatMap(Protocol::of)
                    .orElseGet(() -> protocolFromRequest(request)); // TODO - when Spec is done - UnsupportedVersionError - https://github.com/modelcontextprotocol/modelcontextprotocol/issues/1442
            default -> protocolFromRequest(request);
        };

        FeatureCallReference<T, R> localFeatureCall = rpcRequest.isNotification()
                ? featureCall
                : (features, requestContext, p) -> withManagement(requestContext, rpcRequest.id(), features, () -> featureCall.call(features, requestContext, p));

        return buildFromRpcMessage(rpcRequest, protocol, params, localFeatureCall);
    }

    private <T, R> ParsedRpcMessage buildFromRpcMessage(JsonRpcMessage rpcMessage, Protocol protocol, T params, FeatureCallReference<T, R> featureCall)
    {
        Features features = featuresProvider.featuresFromProtocol(protocol);
        Function<McpRequestContext, ?> feature = requestContext -> featureCall.call(features, requestContext, params);
        return new ParsedRpcMessage(rpcMessage, protocol, features, feature);
    }

    private JsonRpcMessage deserializeJsonRpcMessage(String json)
            throws Exception
    {
        JsonNode tree = jsonMapper.readTree(json);

        if (tree.has("method")) {
            return jsonMapper.convertValue(tree, JsonRpcRequest.class);
        }

        if (tree.has("result") || tree.has("error")) {
            return jsonMapper.convertValue(tree, JsonRpcResponse.class);
        }

        throw exception(PARSE_ERROR, "Cannot deserialize JsonRpcMessage: " + json);
    }

    private <R> R withManagement(McpRequestContext requestContext, Object requestId, Features features, Supplier<R> supplier)
    {
        Optional<LegacySession> session = optionalSessionId(requestContext.request())
                .flatMap(sessionController::session);
        if (session.isEmpty()) {
            return supplier.get();
        }

        if (!httpGetEventsEnabled) {
            features.reconcileVersions(requestContext);
        }

        return cancellationController.<CancelledNotification, R>builder(session.get(), cancellationKey(requestId))
                .withIsCancelledCondition(Optional::isPresent)
                .withReasonMapper(cancellation -> cancellation.flatMap(CancelledNotification::reason))
                .withRequestId(requestId)
                .withPostCancellationAction(LegacySession::deleteValue)
                .executeCancellable(supplier);
    }
}
