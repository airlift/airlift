package io.airlift.mcp;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Module;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodecFactory;
import io.airlift.mcp.model.Icon;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.Protocol;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.HeaderNames.ACCEPT;
import static io.airlift.http.client.HeaderNames.CONTENT_TYPE;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.mcp.TestingIdentityMapper.EXPECTED_IDENTITY;
import static io.airlift.mcp.TestingIdentityMapper.IDENTITY_HEADER;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_06_18;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2025_11_25;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static org.assertj.core.api.Assertions.assertThat;

public class TestMaxProtocolLevel
{
    @Test
    public void testRequestedProtocolAboveMaxIsCapped()
    {
        assertThat(negotiate(Optional.of(PROTOCOL_MCP_2025_11_25), PROTOCOL_MCP_2026_07_28.value()))
                .isEqualTo(PROTOCOL_MCP_2025_11_25.value());

        assertThat(negotiate(Optional.of(PROTOCOL_MCP_2025_06_18), PROTOCOL_MCP_2025_11_25.value()))
                .isEqualTo(PROTOCOL_MCP_2025_06_18.value());
    }

    @Test
    public void testRequestedProtocolBelowMaxIsKept()
    {
        assertThat(negotiate(Optional.of(PROTOCOL_MCP_2025_11_25), PROTOCOL_MCP_2025_06_18.value()))
                .isEqualTo(PROTOCOL_MCP_2025_06_18.value());
    }

    @Test
    public void testUnknownProtocolFallsBackToMax()
    {
        assertThat(negotiate(Optional.of(PROTOCOL_MCP_2025_06_18), "1999-01-01"))
                .isEqualTo(PROTOCOL_MCP_2025_06_18.value());
    }

    @Test
    public void testWithoutMaxLegacyProtocolIsUnchanged()
    {
        assertThat(negotiate(Optional.empty(), PROTOCOL_MCP_2025_11_25.value()))
                .isEqualTo(PROTOCOL_MCP_2025_11_25.value());

        assertThat(negotiate(Optional.empty(), PROTOCOL_MCP_2025_06_18.value()))
                .isEqualTo(PROTOCOL_MCP_2025_06_18.value());
    }

    @Test
    public void testIconsAreStrippedWhenMaxDoesNotSupportThem()
    {
        try (TestingServer testingServer = server(Optional.of(PROTOCOL_MCP_2025_06_18))) {
            assertThat(firstToolHasIcons(testingServer)).isFalse();
        }

        try (TestingServer testingServer = server(Optional.of(PROTOCOL_MCP_2025_11_25))) {
            assertThat(firstToolHasIcons(testingServer)).isTrue();
        }
    }

    private static boolean firstToolHasIcons(TestingServer testingServer)
    {
        Map<String, Object> result = call(testingServer, METHOD_TOOLS_LIST, new ListRequest(Optional.empty(), Optional.empty()));

        assertThat(result.get("tools")).isInstanceOf(List.class);
        List<?> tools = (List<?>) result.get("tools");
        assertThat(tools).isNotEmpty();

        return ((Map<?, ?>) tools.getFirst()).containsKey("icons");
    }

    private static String negotiate(Optional<Protocol> maxProtocolLevel, String requestedProtocolVersion)
    {
        try (TestingServer testingServer = server(maxProtocolLevel)) {
            InitializeRequest initializeRequest = new InitializeRequest(requestedProtocolVersion, ClientCapabilities.EMPTY, new Implementation("test", "1"));
            return (String) call(testingServer, METHOD_INITIALIZE, initializeRequest).get("protocolVersion");
        }
    }

    private static TestingServer server(Optional<Protocol> maxProtocolLevel)
    {
        Function<McpModule.Builder, Module> mcpModuleBuilder = builder -> {
            builder.withIdentityMapper(TestingIdentity.class, binding -> binding.to(TestingIdentityMapper.class).in(SINGLETON))
                    .addIcon("google", binding -> binding.toInstance(new Icon("https://example.com/favicon.ico")))
                    .withAllInClass(TestingEndpoints.class);
            maxProtocolLevel.ifPresent(builder::withMaxProtocolLevel);
            return builder.build();
        };

        return new TestingServer(ImmutableMap.of(), Optional.empty(), mcpModuleBuilder);
    }

    private static Map<String, Object> call(TestingServer testingServer, String method, Object params)
    {
        JsonCodecFactory jsonCodecFactory = new JsonCodecFactory(testingServer.injector().getInstance(JsonMapper.class));
        URI uri = testingServer.injector().getInstance(HttpServerInfo.class).getHttpUri().resolve("/mcp");

        JsonRpcRequest<?> rpcRequest = JsonRpcRequest.buildRequest(1, method, params);

        Request request = preparePost().setUri(uri)
                .addHeader(CONTENT_TYPE, "application/json")
                .addHeader(ACCEPT, "application/json")
                .addHeader(IDENTITY_HEADER, EXPECTED_IDENTITY)
                .setBodyGenerator(jsonBodyGenerator(jsonCodecFactory.jsonCodec(new TypeToken<JsonRpcRequest<?>>() {}), rpcRequest))
                .build();

        JsonResponse<Map<String, Object>> response = testingServer.httpClient()
                .execute(request, createFullJsonResponseHandler(jsonCodecFactory.jsonCodec(new TypeToken<>() {})));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getValue().get("result")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getValue().get("result");
        return result;
    }
}
