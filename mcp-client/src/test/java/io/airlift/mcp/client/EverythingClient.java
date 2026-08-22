package io.airlift.mcp.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.mcp.client.settings.ClientMode;
import io.airlift.mcp.client.settings.LoggingConsumer;
import io.airlift.mcp.client.settings.NotificationConsumer;
import io.airlift.mcp.client.settings.ProgressConsumer;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.ElicitResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Tool;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.client.McpClient.mcpClient;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_NAME;
import static io.airlift.mcp.client.McpClientSetting.CLIENT_VERSION;
import static io.airlift.mcp.client.McpClientSetting.ELICITATION_ENABLED;
import static io.airlift.mcp.client.McpClientSetting.MODE;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_DISABLED;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_ONLY;
import static io.airlift.mcp.client.settings.ClientMode.LEGACY_PROTOCOL_OPTIONAL;
import static io.airlift.mcp.model.Constants.METHOD_ELICITATION_CREATE;
import static io.airlift.mcp.model.Protocol.PROTOCOL_MCP_2026_07_28;
import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;

/**
 * Everything client - a single conformance test client that handles all scenarios. This is the Airlift MCP client
 * equivalent of the conformance suite's example client
 * {@code examples/clients/typescript/everything-client.ts} (commit {@code 74edef3}).
 * <p>
 * Usage: {@code MCP_CONFORMANCE_SCENARIO=<scenario> EverythingClient <server-url>}
 * <p>
 * The scenario name is read from the {@code MCP_CONFORMANCE_SCENARIO} environment variable, which is set by the
 * conformance test runner, and this client routes to the appropriate behavior based on it. The runner also supplies
 * {@code MCP_CONFORMANCE_PROTOCOL_VERSION} (the resolved spec version of the run) and
 * {@code MCP_CONFORMANCE_CONTEXT} (scenario specific JSON - only the unsupported auth scenarios use it, so this
 * client never reads it).
 * <p>
 * Mapping notes against the TypeScript example:
 * <ul>
 *     <li>The TypeScript example hand-rolls a {@code statelessRequest()} fetch shim because the SDK client does not
 *     speak the SEP-2575 stateless lifecycle. The Airlift client does, so the shim maps to
 *     {@link ClientMode#LEGACY_PROTOCOL_DISABLED} and the SDK-client branch to
 *     {@link ClientMode#LEGACY_PROTOCOL_ONLY}; the {@code USE_STATELESS_LIFECYCLE} switch is
 *     {@link #isStatelessConformanceRun()}.</li>
 *     <li>The per-request envelope the shim builds by hand (the {@code MCP-Protocol-Version} header plus the
 *     {@code io.modelcontextprotocol/*} {@code _meta} entries) is attached to every request by the stateless
 *     connection itself.</li>
 *     <li>The {@code request-metadata} handler's hand-rolled -32022 version renegotiation is not reproduced here:
 *     under {@link ClientMode#LEGACY_PROTOCOL_OPTIONAL} the connection itself transparently retries on a fresh
 *     legacy connection when the server rejects the modern protocol, so the fixture just makes the calls.</li>
 *     <li>The SEP-2322 MRTR flow is driven manually with {@link CallToolRequest#withInputResponses}, exactly like
 *     the example's raw JSON-RPC calls, so the unrelated-call isolation check runs between the initial call and the
 *     retry as the example intends.</li>
 * </ul>
 * Client capabilities the Airlift client does not implement are deliberately not emulated here, so a conformance
 * run reports them rather than a fixture-side workaround hiding them: OAuth (all {@code auth/*} scenarios - CIMD,
 * DCR, PKCE, DPoP, JWT-bearer, token exchange and 401 retry) and {@code elicitation.applyDefaults} (the
 * {@code elicitation-defaults} scenario expects the client to fill in schema defaults for omitted fields).
 */
public final class EverythingClient
{
    private static final String MCP_CONFORMANCE_SCENARIO = "MCP_CONFORMANCE_SCENARIO";
    private static final String MCP_CONFORMANCE_PROTOCOL_VERSION = "MCP_CONFORMANCE_PROTOCOL_VERSION";

    /**
     * Spec versions whose wire lifecycle is the SEP-2575 stateless per-request envelope (no {@code initialize}
     * handshake). The TypeScript example imports {@code STATELESS_SPEC_VERSIONS} from the runner's own sources so
     * the mapping cannot drift; this client owns the mapping, as an out-of-repo client must.
     */
    private static final Set<String> STATELESS_SPEC_VERSIONS = ImmutableSet.of(PROTOCOL_MCP_2026_07_28.value());

    /**
     * Auth scenarios from the TypeScript example. They need an OAuth capable client - discovery of protected
     * resource metadata, dynamic client registration, PKCE, DPoP, JWT-bearer grants, token exchange and 401 retry -
     * which the Airlift client does not provide; {@code REQUEST_FILTER} is the extension point for supplying
     * credentials, but the flows themselves would have to be written from scratch. The names are registered so they
     * are listed and so an unimplemented scenario fails loudly instead of looking like an unknown one.
     */
    private static final List<String> AUTH_SCENARIOS = ImmutableList.of(
            "auth/basic-cimd",
            "auth/basic-dcr",
            "auth/metadata-default",
            "auth/metadata-var1",
            "auth/metadata-var2",
            "auth/metadata-var3",
            "auth/2025-03-26-oauth-metadata-backcompat",
            "auth/2025-03-26-oauth-endpoint-fallback",
            "auth/scope-from-www-authenticate",
            "auth/scope-from-scopes-supported",
            "auth/scope-omitted-when-undefined",
            "auth/scope-step-up",
            "auth/scope-retry-limit",
            "auth/token-endpoint-auth-basic",
            "auth/token-endpoint-auth-post",
            "auth/token-endpoint-auth-none",
            "auth/resource-mismatch",
            "auth/offline-access-scope",
            "auth/offline-access-not-supported",
            "auth/iss-supported",
            "auth/iss-not-advertised",
            "auth/iss-supported-missing",
            "auth/iss-wrong-issuer",
            "auth/iss-unexpected",
            "auth/iss-normalized",
            "auth/metadata-issuer-mismatch",
            "auth/authorization-server-migration",
            "auth/client-credentials-jwt",
            "auth/client-credentials-basic",
            "auth/pre-registration",
            "auth/enterprise-managed-authorization",
            "auth/dpop",
            "auth/dpop-nonce",
            "auth/wif-jwt-bearer");

    private static final Map<String, ScenarioHandler> SCENARIO_HANDLERS = buildScenarioHandlers();

    private interface ScenarioHandler
    {
        void run(URI serverUri);
    }

    private EverythingClient() {}

    // ============================================================================
    // Main entry point
    // ============================================================================

    static void main(String[] args)
    {
        String scenarioName = System.getenv(MCP_CONFORMANCE_SCENARIO);
        if ((scenarioName == null) || (args.length != 1)) {
            error("Usage: MCP_CONFORMANCE_SCENARIO=<scenario> %s <server-url>", EverythingClient.class.getName());
            error("The %s env var is set automatically by the conformance runner.", MCP_CONFORMANCE_SCENARIO);
            logAvailableScenarios();
            System.exit(1);
            return;
        }

        runScenario(args[0], scenarioName);
    }

    static void runScenario(String uri, String scenarioName)
    {
        ScenarioHandler handler = SCENARIO_HANDLERS.get(scenarioName);
        if (handler == null) {
            error("Unknown scenario: %s", scenarioName);
            logAvailableScenarios();
            return;
        }

        try {
            handler.run(URI.create(uri));
        }
        catch (Throwable e) {
            error("Error: %s", e);
            e.printStackTrace(System.err);
        }
    }

    private static Map<String, ScenarioHandler> buildScenarioHandlers()
    {
        ImmutableMap.Builder<String, ScenarioHandler> handlers = ImmutableMap.builder();

        handlers.put("initialize", EverythingClient::runBasicClient);
        handlers.put("tools_call", EverythingClient::runBasicClient);
        handlers.put("tools-call", EverythingClient::runBasicClient);
        handlers.put("json-schema-ref-no-deref", EverythingClient::runListToolsOnlyClient);
        handlers.put("json-schema-2020-12-preservation", EverythingClient::runJsonSchemaPreservationClient);
        handlers.put("request-metadata", EverythingClient::runRequestMetadataClient);
        handlers.put("elicitation-defaults", EverythingClient::runElicitationDefaultsClient);
        handlers.put("sep-2322-client-request-state", EverythingClient::runMrtrClient);

        AUTH_SCENARIOS.forEach(name -> handlers.put(name, EverythingClient::runUnsupportedAuthClient));

        return handlers.buildOrThrow();
    }

    // ============================================================================
    // Basic scenarios (initialize, tools_call)
    // ============================================================================

    // These scenarios span spec versions, so the lifecycle follows the resolved version of the run, exactly like
    // the TypeScript example's USE_STATELESS_LIFECYCLE branch: list tools, then call the first advertised tool
    // with { a: 2, b: 3 }.
    private static void runBasicClient(URI serverUri)
    {
        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, lifecycleMode(), "test-client").connect(serverUri)) {
            debug("Successfully connected to MCP server");

            ListToolsResult tools = connection.listTools();
            debug("Successfully listed tools");

            tools.tools().stream()
                    .findFirst()
                    .map(Tool::name)
                    .ifPresent(name -> {
                        connection.callTool(new CallToolRequest(name, ImmutableMap.of("a", 2, "b", 3)));
                        debug("Successfully called tool");
                    });
        }
        debug("Connection closed successfully");
    }

    // SEP-2106: json-schema-ref-no-deref advertises a tool whose inputSchema contains a network-URI $ref. A
    // conformant client lists tools normally and simply never fetches that URI - this client never compiles or
    // dereferences schemas, so listing is sufficient. The scenario's mock only serves tools/list, so this handler
    // stops after listing instead of reusing runBasicClient (whose tools/call would get -32601 and fail the run).
    private static void runListToolsOnlyClient(URI serverUri)
    {
        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, lifecycleMode(), "test-client").connect(serverUri)) {
            debug("Successfully connected to MCP server");

            connection.listTools();
            debug("Successfully listed tools");
        }
        debug("Connection closed successfully");
    }

    // ============================================================================
    // json-schema-2020-12-preservation (SEP-1613, SEP-2106, Issue #101)
    // ============================================================================

    // Scenario contract:
    //   1. tools/list - observe json_schema_2020_12_tool and its inputSchema
    //   2. tools/call json_schema_echo with { schema: <observed inputSchema> }
    // The scenario diffs the echoed schema against its fixture to detect client-side keyword stripping; this
    // handler just round-trips the schema verbatim, which is the compliant behavior. Tool.inputSchema() is an
    // untyped ObjectNode, so nothing is stripped or reordered on the way through.
    private static void runJsonSchemaPreservationClient(URI serverUri)
    {
        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, lifecycleMode(), "test-client").connect(serverUri)) {
            ListToolsResult tools = connection.listTools();

            Tool focal = requireTool(tools, "json_schema_2020_12_tool");
            connection.callTool(new CallToolRequest("json_schema_echo", ImmutableMap.of("schema", focal.inputSchema())));
            debug("Successfully echoed observed inputSchema");
        }
        debug("Connection closed successfully");
    }

    // ============================================================================
    // request-metadata scenario (SEP-2575)
    // ============================================================================

    // Every request must carry the MCP-Protocol-Version header and the io.modelcontextprotocol/* _meta envelope.
    // The connection attaches both to every request it sends, so server/discover followed by tools/list - the
    // example's exact call sequence - is enough to exercise it. The scenario's mock rejects the first request with
    // -32022 no matter what version it carries; under LEGACY_PROTOCOL_OPTIONAL the connection renegotiates
    // transparently, falling back to the legacy protocol and retrying, and the flow completes. The retry check
    // itself still reports a WARNING: the mock's rejection names 2026-07-28 as its only supported version (the very
    // version it just rejected) and upgrades the check only when the retry carries 2026-07-28 again, whereas the
    // transparent renegotiation retries on the legacy 2025-11-25 lifecycle.
    private static void runRequestMetadataClient(URI serverUri)
    {
        debug("Starting request-metadata client flow...");

        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, LEGACY_PROTOCOL_OPTIONAL, "conformance-test-client")
                        .withSetting(ELICITATION_ENABLED, true)
                        .connect(serverUri)) {
            debug("Calling server/discover...");
            debug("Successfully discovered server capabilities: %s", connection.serverDiscover());

            debug("Calling tools/list with inline _meta...");
            debug("Successfully listed tools statelessly: %s", toolNames(connection.listTools()));
        }
        debug("request-metadata client flow completed successfully");
    }

    // ============================================================================
    // Elicitation defaults scenario
    // ============================================================================

    // The tool triggers an elicitation that this client answers with empty content; the example advertises
    // capabilities.elicitation.applyDefaults and relies on the SDK to fill in the schema defaults for every omitted
    // field. This client can advertise elicitation but not the applyDefaults sub-capability, and it does not apply
    // defaults, so the scenario's check is expected to fail - the flow is still driven so the gap is visible.
    private static void runElicitationDefaultsClient(URI serverUri)
    {
        McpInputRequestsHandler inputRequestsHandler = acceptingElicitations(ImmutableMap.of());

        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, LEGACY_PROTOCOL_ONLY, "elicitation-defaults-test-client")
                        .withSetting(ELICITATION_ENABLED, true)
                        .connect(serverUri)) {
            debug("Successfully connected to MCP server");

            ListToolsResult tools = connection.listTools();
            debug("Available tools: %s", toolNames(tools));

            requireTool(tools, "test_client_elicitation_defaults");

            debug("Calling test_client_elicitation_defaults tool...");
            CallToolRequest request = new CallToolRequest("test_client_elicitation_defaults", ImmutableMap.of());
            Optional<CallToolResult> result = inputRequestsHandler.asResultProcessor().process(connection, request, McpConnection::callTool, Optional::of).optional();
            debug("Tool result: %s", result);
        }
        debug("Connection closed successfully");
    }

    // ============================================================================
    // MRTR client conformance (SEP-2322)
    // ============================================================================

    // The scenario measures the multi-round-trip request driver, and this handler drives it manually with
    // CallToolRequest.withInputResponses() - the equivalent of the example's raw JSON-RPC calls - rather than
    // through McpInputRequestsHandler.asResultProcessor(), so the unrelated-call isolation check can run between
    // the initial call and the retry exactly as the example sequences it. Each retry goes out on a fresh JSON-RPC
    // id (the request controller generates a new UUID per request) and echoes the requestState carried by the
    // INPUT_REQUIRED result byte-exact, or omits it when the server sent none.
    private static void runMrtrClient(URI serverUri)
    {
        try (JettyHttpClient httpClient = new JettyHttpClient();
                McpConnection connection = client(httpClient, LEGACY_PROTOCOL_OPTIONAL, "test-client").connect(serverUri)) {
            ListToolsResult tools = connection.listTools();
            debug("Available tools: %s", toolNames(tools));

            // Tool 1: test_mrtr_echo_state - call, get an INPUT_REQUIRED result with requestState, retry
            CallToolRequest echoStateRequest = new CallToolRequest("test_mrtr_echo_state", ImmutableMap.of());
            CallToolResult echoStateResult = connection.callTool(echoStateRequest);
            if (isInputRequired(echoStateResult)) {
                Map<String, Object> inputResponses = fulfillInputRequests(echoStateResult);

                // Call an unrelated tool BEFORE retrying - must NOT carry over inputResponses/requestState
                connection.callTool(new CallToolRequest("test_mrtr_unrelated", ImmutableMap.of()));
                debug("test_mrtr_unrelated: called without MRTR state (isolation check)");

                // Retry with inputResponses + requestState echoed back unchanged
                connection.callTool(echoStateRequest.withInputResponses(echoStateResult.requestState(), inputResponses));
                debug("test_mrtr_echo_state: MRTR flow completed");
            }

            // Tool 2: test_mrtr_no_state - call, get an INPUT_REQUIRED result WITHOUT requestState, retry without it
            CallToolRequest noStateRequest = new CallToolRequest("test_mrtr_no_state", ImmutableMap.of());
            CallToolResult noStateResult = connection.callTool(noStateRequest);
            if (isInputRequired(noStateResult)) {
                connection.callTool(noStateRequest.withInputResponses(noStateResult.requestState(), fulfillInputRequests(noStateResult)));
                debug("test_mrtr_no_state: MRTR flow completed");
            }

            // Tool 3: test_mrtr_no_result_type - returns a result without a resultType field. The client must treat
            // it as complete (the default) and NOT retry.
            CallToolResult noResultTypeResult = connection.callTool(new CallToolRequest("test_mrtr_no_result_type", ImmutableMap.of()));
            if (noResultTypeResult.resultType().isEmpty()) {
                debug("test_mrtr_no_result_type: result has no resultType, treating as complete");
            }

            debug("MRTR client scenario completed");
        }
        debug("Connection closed successfully");
    }

    private static boolean isInputRequired(CallToolResult result)
    {
        return result.resultType()
                .map(resultType -> resultType == INPUT_REQUIRED)
                .orElse(false);
    }

    // Build inputResponses by fulfilling each elicitation/create inputRequest with { confirmed: true }, like the
    // example; requests with any other method are left unanswered.
    private static Map<String, Object> fulfillInputRequests(CallToolResult result)
    {
        ImmutableMap.Builder<String, Object> inputResponses = ImmutableMap.builder();
        result.inputRequests().orElseGet(ImmutableMap::of).forEach((key, inputRequest) -> {
            if (inputRequest.method().equals(METHOD_ELICITATION_CREATE)) {
                inputResponses.put(key, new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(ImmutableMap.of("confirmed", true)), Optional.empty()));
            }
        });
        return inputResponses.buildOrThrow();
    }

    // ============================================================================
    // Auth scenarios
    // ============================================================================

    private static void runUnsupportedAuthClient(URI serverUri)
    {
        throw new UnsupportedOperationException("Auth scenarios are not supported: this client has no OAuth support. " +
                "Credentials can be supplied with the REQUEST_FILTER connection setting, but discovery, registration, " +
                "PKCE, DPoP and token exchange would have to be implemented by the caller. Server: " + serverUri);
    }

    // ============================================================================
    // Helpers
    // ============================================================================

    private static McpClient client(JettyHttpClient httpClient, ClientMode mode, String clientName)
    {
        NotificationConsumer notificationConsumer = (_, method, params) -> debug("notification %s: %s", method, params);
        ProgressConsumer progressConsumer = progressNotification -> debug("progress: %s", progressNotification);
        LoggingConsumer loggingConsumer = notification -> debug("server log: %s", notification);

        return mcpClient(httpClient)
                .withSetting(CLIENT_NAME, clientName)
                .withSetting(CLIENT_VERSION, "1.0.0")
                .withSetting(MODE, mode)
                // the TypeScript example passes `capabilities: {}` unless a scenario needs elicitation
                .withSetting(ELICITATION_ENABLED, false)
                .withDefaultConnectionSetting(
                        McpConnectionSetting.NOTIFICATION_CONSUMER,
                        notificationConsumer.andThen(progressConsumer.asNotificationConsumer())
                                .andThen(loggingConsumer.asNotificationConsumer()));
    }

    /**
     * Answers every input request in a round with an accepted elicitation carrying {@code content}. Under the
     * legacy protocol the result processor routes the server's {@code elicitation/create} request through the same
     * handler, so one handler covers both eras.
     */
    private static McpInputRequestsHandler acceptingElicitations(Map<String, Object> content)
    {
        return inputRequests -> {
            ImmutableMap.Builder<String, Object> inputResponses = ImmutableMap.builder();
            inputRequests.forEach((key, inputRequest) -> {
                if (!inputRequest.method().equals(METHOD_ELICITATION_CREATE)) {
                    throw new IllegalStateException("Unexpected input request method: " + inputRequest.method());
                }
                debug("Received elicitation request: %s", inputRequest.params());
                debug("Accepting with content: %s", content);
                inputResponses.put(key, new ElicitResult(ElicitResult.Action.ACCEPT, Optional.of(content), Optional.empty()));
            });
            return inputResponses.buildOrThrow();
        };
    }

    private static Tool requireTool(ListToolsResult tools, String toolName)
    {
        return tools.tools()
                .stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Test tool not found: " + toolName));
    }

    private static List<String> toolNames(ListToolsResult tools)
    {
        return tools.tools()
                .stream()
                .map(Tool::name)
                .collect(toImmutableList());
    }

    /**
     * Lifecycle decision for version-spanning scenarios, derived from the runner-provided protocol version like
     * the TypeScript example's {@code USE_STATELESS_LIFECYCLE}: the SEP-2575 stateless per-request envelope for a
     * stateless spec version, the stateful {@code initialize} handshake otherwise.
     */
    private static ClientMode lifecycleMode()
    {
        return isStatelessConformanceRun() ? LEGACY_PROTOCOL_DISABLED : LEGACY_PROTOCOL_ONLY;
    }

    private static boolean isStatelessConformanceRun()
    {
        return Optional.ofNullable(System.getenv(MCP_CONFORMANCE_PROTOCOL_VERSION))
                .map(STATELESS_SPEC_VERSIONS::contains)
                .orElse(false);
    }

    private static void logAvailableScenarios()
    {
        error("Available scenarios:");
        SCENARIO_HANDLERS.keySet()
                .stream()
                .sorted()
                .forEach(name -> error("  - %s", name));
    }

    // the conformance runner captures the fixture's stderr as scenario diagnostics
    private static void debug(String format, Object... args)
    {
        System.err.printf(format + "%n", args);
    }

    private static void error(String format, Object... args)
    {
        System.err.printf(format + "%n", args);
    }
}
