package io.airlift.mcp;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closer;
import com.google.inject.Module;
import com.google.inject.binder.LinkedBindingBuilder;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.log.Logger;
import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.tasks.session.SessionTaskController;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import jakarta.ws.rs.core.UriBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.inject.Scopes.SINGLETON;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.ACCEPT;
import static io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action.DECLINE;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.ALERT;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.DEBUG;
import static io.modelcontextprotocol.spec.McpSchema.LoggingLevel.EMERGENCY;
import static io.modelcontextprotocol.spec.McpSchema.Role.USER;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public abstract class MultiServerSessionsTestBase
{
    private static final Logger log = Logger.get(MultiServerSessionsTestBase.class);

    private static final int SERVER_QTY = 3;
    private static final int CLIENT_QTY = 5;

    private static final int SIMULATION_THREAD_QTY = CLIENT_QTY;
    private static final int SIMULATION_ITERATION_QTY = 10;

    protected final TestingContext testingContext;

    private record Client(McpSyncClient mcpClient, String name, List<String> logs, List<String> progress, AtomicReference<Action> nextAction) {}

    protected static class TestingContext
    {
        private final Closer closer = Closer.create();
        private final List<Client> clients;
        private final List<TestingServer> testingServers;
        private final TestingServer timesoutTestingServer;

        protected TestingContext(Function<Closer, Module> testModuleProvider, Consumer<LinkedBindingBuilder<SessionController>> sessionControllerBinding)
        {
            McpIdentityMapper identityMapper = _ -> new McpIdentity.Authenticated<>("yep");
            Module testModule = testModuleProvider.apply(closer);

            testingServers = IntStream.range(0, SERVER_QTY)
                    .mapToObj(_ -> buildTestingServer(ImmutableMap.of(), sessionControllerBinding, testModule, identityMapper))
                    .toList();
            testingServers.forEach(closer::register);

            timesoutTestingServer = buildTestingServer(ImmutableMap.of("mcp.task-emulation.handler-timeout", "1ms"), sessionControllerBinding, testModule, identityMapper);
            closer.register(timesoutTestingServer);

            clients = IntStream.range(0, CLIENT_QTY)
                    .mapToObj(i -> buildClient(closer, testingServers, "Client" + i, true))
                    .toList();
        }
    }

    protected MultiServerSessionsTestBase(TestingContext testingContext)
    {
        this.testingContext = requireNonNull(testingContext, "testingContext is null");
    }

    @AfterAll
    public void shutdown()
            throws IOException
    {
        testingContext.closer.close();
    }

    @BeforeEach
    public void prepare()
    {
        testingContext.clients.forEach(client -> {
            client.logs.clear();
            client.progress.clear();
            client.nextAction.set(ACCEPT);
        });
    }

    @Test
    public void testEmulationTimeout()
    {
        Client client = buildClient(testingContext.closer, List.of(testingContext.timesoutTestingServer), "TimeoutClient", false);
        try {
            CallToolResult callToolResult = client.mcpClient.callTool(new CallToolRequest("testElicitation", ImmutableMap.of()));
            assertThat(callToolResult.isError()).isTrue();
        }
        finally {
            client.mcpClient.close();
        }
    }

    @Test
    public void testTaskThatThrows()
    {
        Client client = testingContext.clients.getFirst();
        CallToolResult callToolResult = client.mcpClient.callTool(new CallToolRequest("taskThatThrows", ImmutableMap.of()));
        assertThat(callToolResult.isError()).isTrue();
        assertThat(callToolResult.content())
                .hasSize(1)
                .first()
                .asInstanceOf(type(McpSchema.TextContent.class))
                .extracting(McpSchema.TextContent::text)
                .asString()
                .contains("Things didn't go well");
    }

    @Test
    public void testProgress()
    {
        List<String> expectedProgress = IntStream.rangeClosed(0, 100)
                .mapToObj(i -> "Progress " + i + "%")
                .collect(toImmutableList());

        for (int i = 0; i < testingContext.clients.size(); i++) {
            Client client = testingContext.clients.get(i);
            client.mcpClient.callTool(new CallToolRequest("progress", ImmutableMap.of()));
            assertThat(client.progress).isEqualTo(expectedProgress);

            // check remaining clients have no progress yet
            for (int j = i + 1; j < testingContext.clients.size(); j++) {
                Client otherClient = testingContext.clients.get(j);
                assertThat(otherClient.progress).isEmpty();
            }
        }
    }

    @Test
    public void testLogging()
    {
        Client client1 = testingContext.clients.getFirst();
        Client client2 = testingContext.clients.getLast();

        client1.mcpClient.setLoggingLevel(EMERGENCY);
        client2.mcpClient.setLoggingLevel(EMERGENCY);

        client1.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        client2.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).isEmpty();
        assertThat(client2.logs).isEmpty();

        client1.mcpClient.setLoggingLevel(ALERT);
        client2.mcpClient.setLoggingLevel(DEBUG);

        client1.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).containsExactly("This is alert");
        assertThat(client2.logs).isEmpty();

        client2.mcpClient.callTool(new CallToolRequest("log", ImmutableMap.of()));
        assertThat(client1.logs).containsExactly("This is alert");
        assertThat(client2.logs).containsExactlyInAnyOrder("This is alert", "This is debug");
    }

    @Test
    public void testElicitationThenSampling()
    {
        testingContext.clients.forEach(client -> {
            CallToolResult callToolResult = client.mcpClient.callTool(new CallToolRequest("elicitationThenSample", ImmutableMap.of()));
            assertThat(callToolResult.content())
                    .hasSize(1)
                    .first()
                    .asInstanceOf(type(McpSchema.TextContent.class))
                    .extracting(McpSchema.TextContent::text)
                    .isEqualTo("Go ahead: %s, %ssky".formatted(client.name, client.name));
        });
    }

    @Test
    public void testElicitation()
    {
        testingContext.clients.forEach(client -> {
            CallToolResult callToolResult = client.mcpClient.callTool(new CallToolRequest("testElicitation", ImmutableMap.of()));
            assertThat(callToolResult.content())
                    .hasSize(1)
                    .first()
                    .asInstanceOf(type(McpSchema.TextContent.class))
                    .extracting(McpSchema.TextContent::text)
                    .isEqualTo("%s, %ssky".formatted(client.name, client.name));
        });
    }

    @Test
    public void simulateMultiUserInteractions()
            throws InterruptedException
    {
        List<Client> testClients = new CopyOnWriteArrayList<>(testingContext.clients);

        enum IterationType { ACCEPT, DECLINE, EXCEPTION }

        record IterationParams(String toolName, Action nextAction, String expectedResult) {}

        AtomicInteger successfulExits = new AtomicInteger(0);
        AtomicInteger tempClientsCreated = new AtomicInteger(0);
        AtomicLong maxElapsedMs = new AtomicLong(0);
        Map<IterationType, AtomicInteger> iterations = new ConcurrentHashMap<>();

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicInteger nextTaskNumber = new AtomicInteger();
            Runnable task = () -> {
                int taskNumber = nextTaskNumber.getAndIncrement();
                for (int i = 0; i < SIMULATION_ITERATION_QTY; i++) {
                    Stopwatch stopwatch = Stopwatch.createStarted();

                    boolean isTempClient = false;
                    Client client;
                    try {
                        client = testClients.remove(ThreadLocalRandom.current().nextInt(testingContext.clients.size()));
                    }
                    catch (ArrayIndexOutOfBoundsException _) {
                        client = buildClient(testingContext.closer, testingContext.testingServers, "Client" + taskNumber + testingContext.clients.size(), false);
                        isTempClient = true;
                        tempClientsCreated.incrementAndGet();
                    }

                    try {
                        Thread.currentThread().setName("SimulationThread-" + client.name);

                        IterationType iterationType = IterationType.values()[ThreadLocalRandom.current().nextInt(IterationType.values().length)];
                        IterationParams iterationParams = switch (iterationType) {
                            case ACCEPT, EXCEPTION -> new IterationParams("elicitationThenSample", ACCEPT, "Go ahead: %s, %ssky".formatted(client.name, client.name));
                            case DECLINE -> new IterationParams("elicitationThenSample", DECLINE, DECLINE.name());
                            //case EXCEPTION -> new IterationParams("taskThatThrows", ACCEPT, "Things didn't go well");
                        };

                        iterations.computeIfAbsent(iterationType, _ -> new AtomicInteger(0))
                                        .incrementAndGet();

                        client.nextAction.set(iterationParams.nextAction);
                        CallToolResult callToolResult = client.mcpClient.callTool(new CallToolRequest(iterationParams.toolName, ImmutableMap.of()));

                        assertThat(callToolResult.content())
                                .hasSize(1)
                                .first()
                                .asInstanceOf(type(McpSchema.TextContent.class))
                                .extracting(McpSchema.TextContent::text)
                                .asString()
                                .contains(iterationParams.expectedResult);

                        long elapsedMs = stopwatch.elapsed().toMillis();
                        maxElapsedMs.updateAndGet(currentMax -> Math.max(currentMax, elapsedMs));
                    }
                    finally {
                        if (isTempClient) {
                            client.mcpClient.close();
                        }
                        else {
                            testClients.add(client);
                        }
                    }
                }

                successfulExits.incrementAndGet();
            };

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < SIMULATION_THREAD_QTY; i++) {
                futures.add(executorService.submit(task));
            }

            executorService.shutdown();

            for (Future<?> future : futures) {
                try {
                    future.get();
                }
                catch (Exception e) {
                    fail("Simulation thread failed: " + e.getMessage());
                }
            }

            if (!executorService.awaitTermination(1, MINUTES)) {
                fail("Executor did not terminate");
            }

            log.info("Max iteration time: %d ms", maxElapsedMs.get());
            log.info("Extra clients created: %d", tempClientsCreated.get());
            log.info("Iterations: %s", iterations);
        }

        assertThat(successfulExits.get()).isEqualTo(SIMULATION_THREAD_QTY);
    }

    private static TestingServer buildTestingServer(Map<String, String> properties, Consumer<LinkedBindingBuilder<SessionController>> sessionControllerBinding, Module testModule, McpIdentityMapper identityMapper)
    {
        return new TestingServer(properties, Optional.of(testModule), builder -> builder
                .withIdentityMapper(McpIdentity.class, binding -> binding.toInstance(identityMapper))
                .withSessions(sessionControllerBinding)
                .withTasks(binding -> binding.to(SessionTaskController.class).in(SINGLETON))
                .build());
    }

    private static Client buildClient(Closer closer, List<TestingServer> testingServers, String name, boolean addToCloser)
    {
        HttpClientStreamableHttpTransport clientTransport = HttpClientStreamableHttpTransport.builder("http://localhost:12345/dummy")
                .httpRequestCustomizer((builder, _, endpoint, _, _) -> {
                    // simulate horizontally scaled servers by randomly choosing a server for each request
                    URI randomServerUri = randomServer(testingServers);
                    builder.uri(UriBuilder.fromUri(endpoint).host(randomServerUri.getHost()).port(randomServerUri.getPort()).build());
                })
                .build();

        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> progress = new CopyOnWriteArrayList<>();

        AtomicReference<Action> nextAction = new AtomicReference<>(ACCEPT);

        McpSyncClient client = McpClient.sync(clientTransport)
                .requestTimeout(Duration.ofSeconds(30))
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).sampling().elicitation().build())
                .elicitation(_ -> new ElicitResult(nextAction.get(), ImmutableMap.of("firstName", name, "lastName", name + "sky")))
                .sampling(createMessageRequest -> new CreateMessageResult(USER, new McpSchema.TextContent("Go ahead: " + ((McpSchema.TextContent) createMessageRequest.messages().getFirst().content()).text()), "dummy", null))
                .progressConsumer(progressNotification -> progress.add(progressNotification.message()))
                .loggingConsumer(loggingNotification -> logs.add(loggingNotification.data()))
                .build();
        client.initialize();

        if (addToCloser) {
            closer.register(client::closeGracefully);
        }

        return new Client(client, name, logs, progress, nextAction);
    }

    private static URI randomServer(List<TestingServer> testingServers)
    {
        return testingServers.get(ThreadLocalRandom.current().nextInt(testingServers.size()))
                .injector()
                .getInstance(TestingHttpServer.class)
                .getBaseUrl();
    }
}
