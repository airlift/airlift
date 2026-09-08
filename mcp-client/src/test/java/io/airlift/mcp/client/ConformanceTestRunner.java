package io.airlift.mcp.client;

import com.sun.net.httpserver.HttpServer;
import io.airlift.json.JsonCodec;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.airlift.json.JsonCodec.jsonCodec;
import static io.airlift.mcp.client.EverythingClient.runScenario;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class ConformanceTestRunner
        implements Closeable
{
    private static final JsonCodec<Spec> SPEC_CODEC = jsonCodec(Spec.class);

    private final HttpServer server;

    public record Spec(String serverUri, String scenario)
    {
        public Spec
        {
            requireNonNull(serverUri, "serverUri is null");
            requireNonNull(scenario, "scenario is null");
        }
    }

    public ConformanceTestRunner()
    {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 100);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        server.createContext("/", exchange -> {
            String command = new String(exchange.getRequestBody().readAllBytes(), UTF_8).trim();
            handleCommand(command);

            exchange.sendResponseHeaders(204, -1);
        });

        server.start();
    }

    public int getPort()
    {
        return server.getAddress().getPort();
    }

    @Override
    public void close()
    {
        server.stop(0);
    }

    private void handleCommand(String command)
    {
        Spec spec = SPEC_CODEC.fromJson(command);
        runScenario(spec.serverUri, spec.scenario);
    }
}
