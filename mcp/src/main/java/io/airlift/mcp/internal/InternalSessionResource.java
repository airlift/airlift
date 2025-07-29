package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.session.ListType;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionId;
import io.airlift.mcp.session.SessionMetadata;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.EOFException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.util.Objects.requireNonNull;

public class InternalSessionResource
{
    private static final Logger log = Logger.get(InternalSessionResource.class);

    public static final String SERVER_TO_CLIENT_ID_SIGNIFIER = "X-SERVER-TO-CLIENT-";

    private final SessionController sessionController;
    private final SessionMetadata sessionMetadata;
    private final ObjectMapper objectMapper;

    @Inject
    public InternalSessionResource(SessionController sessionController, SessionMetadata sessionMetadata, ObjectMapper objectMapper)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.sessionMetadata = requireNonNull(sessionMetadata, "sessionMetadata is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @GET
    @Produces({SERVER_SENT_EVENTS, APPLICATION_JSON})
    public Response sessionStreamHandler(@Context Request request, @Context SessionId sessionId)
    {
        log.debug("Streaming session events for session %s", sessionId);

        StreamingOutput streamingOutput = out -> {
            try (out) {
                InternalNotifier internalNotifier = new InternalNotifier(request, Optional.of(sessionController), sessionId, objectMapper, Optional::empty, out);
                sessionStreamingLoop(internalNotifier, sessionId);
            }
        };
        BufferDefeatingStreamingOutput wrapper = new BufferDefeatingStreamingOutput(streamingOutput);
        return Response.ok(wrapper)
                .header(CONTENT_TYPE, SERVER_SENT_EVENTS)
                .build();
    }

    @DELETE
    public Response deleteSession(@Context SessionId sessionId)
    {
        log.debug("Deleting session %s", sessionId);

        sessionController.deleteSession(sessionId);

        return Response.accepted().build();
    }

    @SuppressWarnings("BusyWait")
    private void sessionStreamingLoop(InternalNotifier internalNotifier, SessionId sessionId)
    {
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            boolean supportsRoots = sessionController.clientCapabilities(sessionId)
                    .map(clientCapabilities -> clientCapabilities.roots().isPresent())
                    .orElse(false);
            if (supportsRoots) {
                log.debug("Sending \"roots/list\" for new streaming of session %s", sessionId);

                sendRootsListRequest(internalNotifier);
            }

            while (true) {
                if (sessionController.parseAndValidate(sessionId.asString()).isEmpty()) {
                    log.debug("Session has expired. Exiting event loop for: %s", sessionId);
                    break;
                }

                if (stopwatch.elapsed().compareTo(sessionMetadata.maxEventsLoopDuration()) > 0) {
                    log.debug("Max event loop duration %s exceeded for session %s. Exiting event loop.", sessionMetadata.maxEventsLoopDuration(), sessionId);
                    break;
                }

                Thread.sleep(sessionMetadata.sessionUpdateCadence().toMillis());

                Set<ListType> changedLists = sessionController.takeChangedLists(sessionId);
                changedLists.forEach(listType -> {
                    switch (listType) {
                        case TOOLS -> internalNotifier.sendNotification(NOTIFICATION_TOOLS_LIST_CHANGED);
                        case PROMPTS -> internalNotifier.sendNotification(NOTIFICATION_PROMPTS_LIST_CHANGED);
                        case RESOURCES -> internalNotifier.sendNotification(NOTIFICATION_RESOURCES_LIST_CHANGED);
                        case ROOTS -> sendRootsListRequest(internalNotifier);
                    }
                });

                List<String> resourceUpdates = sessionController.takeResourceUpdates(sessionId);
                resourceUpdates.forEach(resource -> {
                    ResourcesUpdatedNotification notification = new ResourcesUpdatedNotification(resource);
                    internalNotifier.sendNotification(NOTIFICATION_RESOURCES_UPDATED, notification);
                });

                List<JsonRpcRequest<?>> serverToClientRequests = sessionController.takeServerToClientRequests(sessionId);
                serverToClientRequests.forEach(internalNotifier::sendRequest);

                if (changedLists.isEmpty() && resourceUpdates.isEmpty() && serverToClientRequests.isEmpty()) {
                    JsonRpcRequest<Object> rpcRequest = JsonRpcRequest.buildRequest(internalNotifier.nextEventId(), METHOD_PING);
                    internalNotifier.writeRequest(rpcRequest);
                }
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while waiting for session %s", sessionId);
        }
        catch (Exception e) {
            //noinspection SimplifyStreamApiCallChains
            boolean handled = Throwables.getCausalChain(e)
                    .stream()
                    .filter(t -> {
                        if (t instanceof EOFException) {
                            log.debug("Client disconnected from session stream. Exiting event loop for: %s", sessionId);
                            return true;
                        }

                        if (t instanceof McpException mcpException) {
                            internalNotifier.sendNotification(NOTIFICATION_MESSAGE, mcpException.errorDetail());
                            return true;
                        }

                        return false;
                    })
                    .findFirst()
                    .isPresent();
            if (!handled) {
                log.error("Exception while dispatching request %s", sessionId, e);
            }
        }
    }

    private static void sendRootsListRequest(InternalNotifier internalNotifier)
    {
        JsonRpcRequest<Object> request = JsonRpcRequest.buildRequest(METHOD_ROOTS_LIST, METHOD_ROOTS_LIST);
        internalNotifier.writeRequest(request);
    }
}
