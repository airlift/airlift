package io.airlift.mcp.client.internal.legacy;

import com.google.common.collect.ImmutableListMultimap;
import io.airlift.http.client.UnexpectedResponseException;
import io.airlift.mcp.client.settings.StandardExceptionMapper;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.client.internal.legacy.LegacyConnection.isEndedSession;
import static org.assertj.core.api.Assertions.assertThat;

public class TestLegacySession
{
    private static final InitializeResult INITIALIZE_RESULT = new InitializeResult("2025-11-25", new ServerCapabilities(), new Implementation("server", "1"), Optional.empty());

    @Test
    public void testRestartStartsOneNewSessionPerEndedSession()
    {
        AtomicInteger sessions = new AtomicInteger();
        LegacySession session = new LegacySession(() -> new LegacySession.State(INITIALIZE_RESULT, Optional.of("session-" + sessions.incrementAndGet())));
        assertThat(session.state().sessionId()).contains("session-1");

        assertThat(session.restart(Optional.of("session-1")).sessionId()).contains("session-2");

        // another request that failed in the ended session finds it already replaced
        assertThat(session.restart(Optional.of("session-1")).sessionId()).contains("session-2");
        assertThat(sessions).hasValue(2);

        // the new session can end in turn
        assertThat(session.restart(Optional.of("session-2")).sessionId()).contains("session-3");
        assertThat(sessions).hasValue(3);
    }

    @Test
    public void testNotFoundForASessionMeansTheSessionEnded()
    {
        assertThat(isEndedSession(unexpectedResponse(404), Optional.of("session-1"))).isTrue();
    }

    @Test
    public void testOtherFailuresDoNotMeanTheSessionEnded()
    {
        // without a session, a 404 is about something else - such as the endpoint
        assertThat(isEndedSession(unexpectedResponse(404), Optional.empty())).isFalse();

        assertThat(isEndedSession(unexpectedResponse(400), Optional.of("session-1"))).isFalse();
        assertThat(isEndedSession(unexpectedResponse(500), Optional.of("session-1"))).isFalse();
        assertThat(isEndedSession(new StandardExceptionMapper().mapException(new IllegalStateException("local failure")), Optional.of("session-1"))).isFalse();
    }

    private static RuntimeException unexpectedResponse(int statusCode)
    {
        UnexpectedResponseException cause = new UnexpectedResponseException(
                "HTTP " + statusCode,
                preparePost().setUri(URI.create("http://localhost:1/mcp")).build(),
                statusCode,
                ImmutableListMultimap.of());
        return new StandardExceptionMapper().mapException(cause);
    }
}
