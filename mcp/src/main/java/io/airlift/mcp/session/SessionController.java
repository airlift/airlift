package io.airlift.mcp.session;

import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.RootsResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SessionController
{
    /**
     * Create a new session based on the provided request.
     */
    SessionId createSession(InitializeRequest request);

    /**
     * Delete the session with the given ID (if it is valid). This method should clean up any resources
     * associated with the session, such as tools, prompts, and other session-related entities.
     * Note: it is the MCP client's responsibility to delete sessions, and it is optional
     * behavior. Therefore, your controller should periodically clean up sessions.
     */
    void deleteSession(SessionId sessionId);

    /**
     * Parse and validate the provided session ID string. Return {@code Optional.empty()}
     * if the session ID is invalid for any reason.
     */
    Optional<SessionId> parseAndValidate(String sessionId);

    /**
     * Return the client capabilities for the session. Return {@code Optional.empty()}
     * if the session ID is invalid for any reason.
     */
    Optional<ClientCapabilities> clientCapabilities(SessionId sessionId);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts,
     * resources, and other session-related entities. The MCP Server will periodically
     * call this method (per {@link SessionMetadata#sessionUpdateCadence()}) to
     * take any changes or updates that have occurred since the last call for this session. The session controller
     * should return a set of entity lists that have changed since the last call for this session, clearing the saved set
     * so that a subsequent call will not return the same set of changed entities. Return an empty set
     * if the session ID is invalid for any reason.
     */
    Set<ListType> takeChangedLists(SessionId sessionId);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts,
     * resources, and other session-related entities. The MCP Server will periodically
     * call this method (per {@link SessionMetadata#sessionUpdateCadence()}) to
     * take any changes or updates that have occurred since the last call for this session. The session controller
     * should return a list of resource updates that have occurred, clearing the saved set
     * so that a subsequent call will not return the same set of changed entities.
     * Return an empty list if the session ID is invalid for any reason.
     */
    List<String> takeResourceUpdates(SessionId sessionId);

    /**
     * <p>
     *     The session controller is responsible for managing the lifecycle of tools, prompts,
     *     resources, and other session-related entities. The MCP Server will periodically
     *     call this method (per {@link SessionMetadata#sessionUpdateCadence()})
     * </p>
     *
     * <p>
     *     Any server-to-client requests that should be sent to the client. IMPORTANT: the responses to the requests will be
     *     sent in a different context as clients send responses as single HTTP POSTs to the server.
     *     In a horizontally scaled environment, it may even be a different server receiving the response.
     *     Clearing the saved set so that a subsequent call will not return the same set of requests.
     *     Return an empty list if the session ID is invalid for any reason.
     * </p>
     *
     * <p>
     *     Use the request ID to correlate the request with the response. Note: your request ID should
     *     be a {@code String}.
     * </p>
     *
     * <p>
     *     Use for server-to-client features such as sampling and elicitation.
     * </p>
     */
    List<JsonRpcRequest<?>> takeServerToClientRequests(SessionId sessionId);

    /**
     * Returns the state of the given request. If the request is
     * currently running on any server the state returned will be
     * {@link RequestState#STARTED}. If the request is not running on any server
     * or isn't known, the state returned will be {@link RequestState#ENDED}. If
     * any client has requested that the request be cancelled, the state returned will be
     * {@link RequestState#CANCELLATION_REQUESTED}.
     */
    RequestState requestState(SessionId sessionId, String requestId);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts,
     * resources, and other session-related entities. Called when a request state has changed.
     */
    void acceptRequestState(SessionId sessionId, String requestId, RequestState state);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts,
     * resources, and other session-related entities. Return the current list of
     * roots in the session or an empty list if the session ID is invalid for any reason.
     */
    List<String> roots(SessionId sessionId);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts, resources,
     * and other session-related entities. This method is called to accept a new roots
     * notification from the client. The controller
     * must save this state in the session (if it is valid) so that a future call to {@link SessionController#roots(SessionId)}
     * for {@code ChangedLists} returns {@link ListType#ROOTS} for the session.
     */
    void acceptRoots(SessionId sessionId, List<RootsResponse.Root> roots);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts, resources,
     * and other session-related entities. This method is called when the server receives
     * initial or updated roots from the client.
     */
    void acceptRootsChanged(SessionId sessionId);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts, resources,
     * and other session-related entities. Can be used by tools or anywhere else in your code to queue a request to be sent to the client.The controller
     * must save this state in the session (if it is valid) so that a future call to {@link #takeServerToClientRequests(SessionId)}
     * for {@code ServerToClientRequests} returns this request for the session.
     */
    void acceptClientToServerRequest(SessionId sessionId, JsonRpcRequest<?> request);

    /**
     * The session controller is responsible for managing the lifecycle of tools, prompts, resources,
     * and other session-related entities. Called when the client sends a response to a server-to-client request. IMPORTANT: the responses
     * to the requests will be sent in a different context as clients send responses as single HTTP POSTs
     * to the server. In a horizontally scaled environment, it may even be a different server receiving the response.
     * Use the request ID to correlate the request with the response.
     */
    void acceptServerToClientResponse(SessionId sessionId, JsonRpcResponse<Map<String, Object>> response);

    /**
     * Change this session's subscription to the given URI (if the session/uri is valid)
     */
    void changeResourceSubscription(SessionId sessionId, String uri, SubscribeType subscribeType);

    /**
     * Return the logging level for the given session (if it is valid) or a default level if not set.
     */
    LoggingLevel loggingLevel(SessionId sessionId);

    /**
     * Set the logging level for the given session (if it is valid).
     */
    void setLoggingLevel(SessionId sessionId, LoggingLevel level);
}
