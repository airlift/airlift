package io.airlift.mcp.client.legacy;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.HeaderName;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.mcp.client.McpClient;
import io.airlift.mcp.client.McpConnection;
import io.airlift.mcp.client.TestMcpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.airlift.http.client.Request.Builder.prepareDelete;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.McpConnectionSetting.MAX_INPUT_REQUEST_ROUNDS;
import static io.airlift.mcp.client.McpConnectionSetting.REQUEST_FILTER;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_ONLY;
import static io.airlift.mcp.model.Constants.HEADER_MCP_METHOD;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMcpClientLegacy
        extends TestMcpClient
{
    public TestMcpClientLegacy()
    {
        super(LEGACY_PROTOCOL_ONLY);
    }

    @Test
    public void testEndedSessionIsReplaced()
    {
        List<Request> sent = new CopyOnWriteArrayList<>();
        HttpRequestFilter recordingFilter = request -> {
            sent.add(request);
            return request;
        };

        try (JettyHttpClient recordingHttpClient = new JettyHttpClient("recording", new HttpClientConfig(), ImmutableList.of(recordingFilter))) {
            McpClient client = mcpClient(recordingHttpClient)
                    .withSetting(MODE, LEGACY_PROTOCOL_ONLY)
                    .withDefaultConnectionSetting(REQUEST_FILTER, builder -> builder.setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY));

            try (McpConnection connection = client.connect(uri())) {
                McpConnection derived = connection.withSetting(MAX_INPUT_REQUEST_ROUNDS, 3);
                assertThat(connection.listTools().tools()).isNotEmpty();
                String endedSessionId = lastSessionId(sent);

                // the server ends the session, after which it answers requests naming it with a 404
                recordingHttpClient.execute(
                        prepareDelete()
                                .setUri(uri())
                                .setHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                                .setHeader(HeaderName.of(HEADER_SESSION_ID), endedSessionId)
                                .build(),
                        createStatusResponseHandler());
                long initializes = initializeCount(sent);

                // a new session is started and the request sent again in it
                assertThat(connection.listTools().tools()).isNotEmpty();
                assertThat(initializeCount(sent)).isEqualTo(initializes + 1);
                assertThat(lastSessionId(sent)).isNotEqualTo(endedSessionId);

                // a derived connection is in the same session, so it uses the new one rather than starting another
                assertThat(derived.listTools().tools()).isNotEmpty();
                assertThat(initializeCount(sent)).isEqualTo(initializes + 1);
            }
        }
    }

    private static String lastSessionId(List<Request> sent)
    {
        return Optional.ofNullable(sent.getLast().getHeader(HeaderName.of(HEADER_SESSION_ID))).orElseThrow();
    }

    private static long initializeCount(List<Request> sent)
    {
        return sent.stream()
                .filter(request -> METHOD_INITIALIZE.equals(request.getHeader(HeaderName.of(HEADER_MCP_METHOD))))
                .count();
    }
}
