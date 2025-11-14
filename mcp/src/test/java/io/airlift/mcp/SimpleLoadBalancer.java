package io.airlift.mcp;

import com.google.common.collect.ImmutableList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

// mostly from Claude.ai
public class SimpleLoadBalancer
        implements Closeable
{
    private final List<URI> backends;
    private final HttpClient httpClient;
    private final HttpServer server;
    private final URI baseUri;

    public SimpleLoadBalancer(List<URI> backends)
    {
        this.backends = ImmutableList.copyOf(backends);
        this.httpClient = HttpClient.newHttpClient();

        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", new LoadBalancerHandler());
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        baseUri = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @Override
    public void close()
    {
        server.stop(0);
    }

    public URI getBaseUri()
    {
        return baseUri;
    }

    private class LoadBalancerHandler
            implements HttpHandler
    {
        @Override
        public void handle(HttpExchange exchange)
                throws IOException
        {
            URI backend = backends.get(ThreadLocalRandom.current().nextInt(backends.size()));

            try {
                // Forward request to backend
                String targetUrl = backend + exchange.getRequestURI().toString();

                // Build request with request body if present
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(Duration.ofMinutes(30)); // Long timeout for streaming

                // Copy headers
                exchange.getRequestHeaders()
                        .entrySet()
                        .stream()
                        .filter(entry -> !entry.getKey().equalsIgnoreCase("Host") && !entry.getKey().equalsIgnoreCase("Content-Length"))
                        .forEach(entry -> entry.getValue().forEach(value -> requestBuilder.header(entry.getKey(), value)));

                // Handle request body
                HttpRequest.BodyPublisher bodyPublisher;
                if (exchange.getRequestBody().available() > 0) {
                    byte[] requestBody = exchange.getRequestBody().readAllBytes();
                    bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(requestBody);
                }
                else {
                    bodyPublisher = HttpRequest.BodyPublishers.noBody();
                }

                HttpRequest request = requestBuilder
                        .method(exchange.getRequestMethod(), bodyPublisher)
                        .build();

                // Send request and stream response
                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());

                // Copy response headers
                response.headers().map().forEach((key, values) -> {
                    if (!key.equalsIgnoreCase("Transfer-Encoding")) { // Let server handle this
                        values.forEach(value -> exchange.getResponseHeaders().add(key, value));
                    }
                });

                // Start streaming response - chunked encoding with 0 length
                exchange.sendResponseHeaders(response.statusCode(), 0);

                // Stream the response body
                try (InputStream responseBody = response.body(); OutputStream clientOutput = exchange.getResponseBody()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;

                    while ((bytesRead = responseBody.read(buffer)) != -1) {
                        clientOutput.write(buffer, 0, bytesRead);
                        clientOutput.flush(); // Flush immediately for streaming
                    }
                }
            }
            catch (Exception e) {
                // Handle backend failure
                String errorMsg = "Backend error: " + e.getMessage();
                exchange.sendResponseHeaders(502, errorMsg.length());
                OutputStream os = exchange.getResponseBody();
                os.write(errorMsg.getBytes());
                os.close();
            }
        }
    }
}
