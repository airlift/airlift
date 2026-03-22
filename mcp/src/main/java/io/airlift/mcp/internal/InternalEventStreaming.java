package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.SentMessages;
import io.airlift.mcp.legacy.LegacyEventStreaming;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionController;
import io.airlift.mcp.legacy.sessions.LegacySessionId;
import io.airlift.mcp.versions.VersionsController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static io.airlift.mcp.legacy.sessions.LegacySessionController.optionalSessionId;
import static io.airlift.mcp.legacy.sessions.LegacySessionController.requireSessionId;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.SENT_MESSAGES;
import static io.airlift.mcp.model.Constants.HEADER_LAST_EVENT_ID;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.Objects.requireNonNull;

class InternalEventStreaming
        implements LegacyEventStreaming
{
    private static final Logger log = Logger.get(InternalEventStreaming.class);

    private final JsonMapper jsonMapper;
    private final Duration streamingTimeout;
    private final Duration streamingPingThreshold;
    private final int maxResumableMessages;
    private final LegacySessionController sessionController;
    private final VersionsController versionsController;

    @Inject
    InternalEventStreaming(JsonMapper jsonMapper, McpConfig mcpConfig, VersionsController versionsController, LegacySessionController sessionController)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.versionsController = requireNonNull(versionsController, "versionsController is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        streamingTimeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
        streamingPingThreshold = mcpConfig.getEventStreamingPingThreshold().toJavaTime();
        maxResumableMessages = mcpConfig.getMaxResumableMessages();
    }

    @Override
    public void handleEventStreaming(McpRequestContext requestContext)
    {
        LegacySession legacySession = requireSession(requestContext);
        handleEventStreaming(requestContext.request(), requestContext.response(), legacySession, requestContext.identity());
    }

    @Override
    public void checkSaveSentMessages(McpRequestContext requestContext)
    {
        Optional.ofNullable(requestContext.request().getAttribute(MESSAGE_WRITER_ATTRIBUTE))
                .map(InternalMessageWriter.class::cast)
                .ifPresent(messageWriter -> optionalSessionId(requestContext.request())
                        .flatMap(sessionController::session)
                        .ifPresent(session -> checkSaveSentMessages(session, messageWriter)));
    }

    @SuppressWarnings("BusyWait")
    private void handleEventStreaming(HttpServletRequest request, HttpServletResponse response, LegacySession session, McpIdentity.Authenticated<?> authenticated)
    {
        Stopwatch timeoutStopwatch = Stopwatch.createStarted();
        Stopwatch pingStopwatch = Stopwatch.createStarted();

        InternalMessageWriter messageWriter = new InternalMessageWriter(response);
        InternalRequestContext requestContext = new InternalRequestContext(jsonMapper, Optional.of(session), request, response, messageWriter, authenticated);

        Optional.ofNullable(request.getHeader(HEADER_LAST_EVENT_ID))
                .ifPresent(lastEventId -> replaySentMessages(session, lastEventId, messageWriter));

        while (timeoutStopwatch.elapsed().compareTo(streamingTimeout) < 0) {
            if (!session.isValid()) {
                log.warn("Session is invalid for session %s, ending event stream", session.sessionId());
                break;
            }

            BiConsumer<String, Optional<Object>> notifier = (method, params) -> {
                requestContext.sendMessage(method, params);

                pingStopwatch.reset().start();
            };

            versionsController.reconcileVersions(requestContext, session);

            checkSaveSentMessages(session, messageWriter);

            if (pingStopwatch.elapsed().compareTo(streamingPingThreshold) >= 0) {
                notifier.accept(METHOD_PING, Optional.empty());
            }

            try {
                Thread.sleep(streamingPingThreshold.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Event streaming interrupted for session %s", session.sessionId());
                break;
            }
        }
    }

    private void replaySentMessages(LegacySession session, String lastEventId, InternalMessageWriter messageWriter)
    {
        session.getValue(SENT_MESSAGES)
                .ifPresent(sentMessages -> {
                    boolean found = false;
                    for (SentMessages.SentMessage sentMessage : sentMessages.messages()) {
                        if (found) {
                            log.info("Sending resumable messages to session %s", session.sessionId());
                            messageWriter.internalWriteMessage(sentMessage.id(), sentMessage.data());
                        }
                        else {
                            found = sentMessage.id().equals(lastEventId);
                        }
                    }
                    messageWriter.flushMessages();
                });
    }

    private void checkSaveSentMessages(LegacySession session, InternalMessageWriter messageWriter)
    {
        List<SentMessages.SentMessage> sentMessages = messageWriter.takeSentMessages();
        if (sentMessages.isEmpty()) {
            return;
        }

        log.debug("Saving sent messages for session %s: %s", session.sessionId(), sentMessages);
        session.signalAll();

        session.computeValue(SENT_MESSAGES, current -> {
            SentMessages currentSentMessages = current.orElseGet(SentMessages::new);
            return Optional.of(currentSentMessages.withAdditionalMessages(sentMessages, maxResumableMessages));
        });
    }

    LegacySession requireSession(McpRequestContext requestContext)
    {
        LegacySessionId sessionId = requireSessionId(requestContext.request());
        return sessionController.session(sessionId)
                .orElseThrow(() -> new WebApplicationException(NOT_FOUND));
    }
}
