package io.airlift.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.CreateMessageResult;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Role;
import io.airlift.mcp.model.RootsResponse.Root;
import io.airlift.mcp.session.ListType;
import io.airlift.mcp.session.RequestState;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionId;
import io.airlift.mcp.session.SubscribeType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Objects.requireNonNull;

public class TestingSessionController
        implements SessionController
{
    private final Map<SessionId, Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    private static class Session
    {
        final InitializeRequest request;
        final Set<ListType> changedLists = Sets.newConcurrentHashSet();
        final List<String> resourceUpdates = new CopyOnWriteArrayList<>();
        final Set<String> subscribedResources = Sets.newConcurrentHashSet();
        final List<JsonRpcRequest<?>> clientToServerRequests = new CopyOnWriteArrayList<>();
        final List<String> roots = new CopyOnWriteArrayList<>();
        final List<CreateMessageResult> createMessageResults = new CopyOnWriteArrayList<>();
        final Map<Object, RequestState> requestStates = new ConcurrentHashMap<>();
        volatile LoggingLevel loggingLevel = LoggingLevel.debug;

        Session(InitializeRequest request)
        {
            this.request = requireNonNull(request, "request is null");
        }
    }

    @Inject
    public TestingSessionController(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @Override
    public SessionId createSession(InitializeRequest request)
    {
        TestingSessionId sessionId = TestingSessionId.random();
        sessions.put(sessionId, new Session(request));
        return sessionId;
    }

    @Override
    public Optional<ClientCapabilities> clientCapabilities(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> session.request.capabilities());
    }

    @Override
    public Optional<SessionId> parseAndValidate(String sessionId)
    {
        try {
            TestingSessionId testingSessionId = TestingSessionId.parse(sessionId);
            return sessions.containsKey(testingSessionId) ? Optional.of(testingSessionId) : Optional.empty();
        }
        catch (Exception _) {
            // ignore
        }
        return Optional.empty();
    }

    @Override
    public RequestState requestState(SessionId sessionId, String requestId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> session.requestStates.getOrDefault(requestId, RequestState.ENDED))
                .orElse(RequestState.ENDED);
    }

    @Override
    public Set<ListType> takeChangedLists(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> {
                    Set<ListType> changedLists = ImmutableSet.copyOf(session.changedLists);
                    session.changedLists.clear();
                    return changedLists;
                })
                .orElseGet(ImmutableSet::of);
    }

    @Override
    public List<String> takeResourceUpdates(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> {
                    List<String> resourceUpdates = ImmutableList.copyOf(session.resourceUpdates);
                    session.resourceUpdates.clear();
                    return resourceUpdates;
                })
                .orElseGet(ImmutableList::of);
    }

    @Override
    public List<JsonRpcRequest<?>> takeServerToClientRequests(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> {
                    List<JsonRpcRequest<?>> clientToServerRequests = ImmutableList.copyOf(session.clientToServerRequests);
                    session.clientToServerRequests.clear();
                    return clientToServerRequests;
                })
                .orElseGet(ImmutableList::of);
    }

    @Override
    public void changeResourceSubscription(SessionId sessionId, String uri, SubscribeType subscribeType)
    {
        switch (subscribeType) {
            case SUBSCRIBE -> Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.subscribedResources.add(uri));

            case UNSUBSCRIBE -> Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.subscribedResources.remove(uri));
        }
    }

    @Override
    public void setLoggingLevel(SessionId sessionId, LoggingLevel level)
    {
        Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.loggingLevel = level);
    }

    @Override
    public LoggingLevel loggingLevel(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId)).map(sessionn -> sessionn.loggingLevel).orElse(LoggingLevel.debug);
    }

    @Override
    public void deleteSession(SessionId sessionId)
    {
        sessions.remove(sessionId);
    }

    List<CreateMessageResult> takeCreateMessageResults(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> {
                    List<CreateMessageResult> createMessageResults = ImmutableList.copyOf(session.createMessageResults);
                    session.createMessageResults.clear();
                    return createMessageResults;
                })
                .orElseGet(ImmutableList::of);
    }

    void simulateListChanged(SessionId sessionId, ListType listType)
    {
        Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.changedLists.add(listType));
    }

    boolean simulateResourcesUpdated(SessionId sessionId, String uri)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .filter(session -> session.subscribedResources.contains(uri))
                .map(session -> {
                    session.resourceUpdates.add(uri);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public List<String> roots(SessionId sessionId)
    {
        return Optional.ofNullable(sessions.get(sessionId))
                .map(session -> session.roots)
                .orElseGet(ImmutableList::of);
    }

    @Override
    public void acceptRequestState(SessionId sessionId, String requestId, RequestState state)
    {
        Optional.ofNullable(sessions.get(sessionId))
                .ifPresent(session -> session.requestStates.put(requestId, state));
    }

    @Override
    public void acceptServerToClientResponse(SessionId sessionId, JsonRpcResponse<Map<String, Object>> response)
    {
        if (String.valueOf(response.id()).equals("sendSamplingMessage")) {
            CreateMessageResult createMessageResult =
                    response.error()
                            .map(error -> new CreateMessageResult(Role.user, new TextContent(error.message()), "", Optional.empty()))
                            .orElseGet(() -> objectMapper.convertValue(response.result().orElseThrow(), CreateMessageResult.class));
            Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.createMessageResults.add(createMessageResult));
        }
    }

    @Override
    public void acceptRoots(SessionId sessionId, List<Root> roots)
    {
        Optional.ofNullable(sessions.get(sessionId))
                .ifPresent(session -> {
                    session.roots.clear();
                    roots.forEach(root -> session.roots.add(root.uri()));
                });
    }

    @Override
    public void acceptRootsChanged(SessionId sessionId)
    {
        simulateListChanged(sessionId, ListType.ROOTS);
    }

    @Override
    public void acceptClientToServerRequest(SessionId sessionId, JsonRpcRequest<?> request)
    {
        Optional.ofNullable(sessions.get(sessionId)).ifPresent(session -> session.clientToServerRequests.add(request));
    }
}
