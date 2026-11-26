/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.http.client.jetty;

import io.airlift.http.client.EchoServlet;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StreamingResponse;
import io.airlift.http.client.TestingHttpServer;
import io.airlift.units.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.Request.Builder.preparePost;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(60)
public class TestJettyHttpClientLogging
{
    private static final int STATUS_COLUMN = 4;
    private static final int RESPONSE_SIZE_COLUMN = 5;

    private static final byte[] REQUEST_BODY = "r".repeat(1000).getBytes(US_ASCII);
    private static final String SMALL_RESPONSE = "s".repeat(37);
    // large enough to be delivered to the client in several chunks
    private static final String LARGE_RESPONSE = "l".repeat(1024 * 1024);

    @TempDir
    private Path logDirectory;

    @Test
    public void testResponseSize()
            throws Exception
    {
        for (boolean http2 : new boolean[] {false, true}) {
            for (String responseBody : List.of(SMALL_RESPONSE, LARGE_RESPONSE)) {
                assertResponseSizeLogged("sync", http2, responseBody, (client, request) -> client.execute(request, createStringResponseHandler()));
                assertResponseSizeLogged("async", http2, responseBody, (client, request) -> client.executeAsync(request, createStringResponseHandler()).get());
                assertResponseSizeLogged("streaming", http2, responseBody, (client, request) -> {
                    try (StreamingResponse response = client.executeStreaming(request)) {
                        assertThat(response.getInputStream().readAllBytes()).hasSize(responseBody.length());
                    }
                });
            }
        }
    }

    private void assertResponseSizeLogged(String name, boolean http2, String responseBody, RequestExecutor executor)
            throws Exception
    {
        String clientName = "%s-%s-%s".formatted(name, http2 ? "http2" : "http1", responseBody.length());
        EchoServlet servlet = new EchoServlet();
        servlet.setResponseBody(responseBody);
        try (TestingHttpServer server = new TestingHttpServer(Optional.empty(), servlet);
                JettyHttpClient client = new JettyHttpClient(clientName, clientConfig().setHttp2Enabled(http2))) {
            executor.execute(client, preparePost()
                    .setUri(server.baseURI())
                    .setBodyGenerator(createStaticBodyGenerator(REQUEST_BODY))
                    .build());
            assertThat(servlet.getRequestBytes()).hasSize(REQUEST_BODY.length);
            executor.execute(client, prepareGet().setUri(server.baseURI()).build());

            assertThat(awaitLogLines(clientName, 2))
                    .as(clientName)
                    .allSatisfy(columns -> {
                        assertThat(columns[STATUS_COLUMN]).isEqualTo("200");
                        assertThat(columns[RESPONSE_SIZE_COLUMN]).isEqualTo(Integer.toString(responseBody.length()));
                    });
        }
    }

    @Test
    public void testFailedResponseSize()
            throws Exception
    {
        try (TruncatedResponseServer server = new TruncatedResponseServer()) {
            Request request = prepareGet().setUri(server.uri()).build();

            try (JettyHttpClient client = new JettyHttpClient("failed-sync", clientConfig())) {
                assertThatThrownBy(() -> client.execute(request, createStringResponseHandler()))
                        .hasRootCauseInstanceOf(IOException.class);
                assertThat(awaitLogLines("failed-sync", 1))
                        .allSatisfy(columns -> assertThat(columns[RESPONSE_SIZE_COLUMN]).isEqualTo(Integer.toString(TruncatedResponseServer.DELIVERED.length())));
            }

            try (JettyHttpClient client = new JettyHttpClient("failed-async", clientConfig())) {
                assertThatThrownBy(() -> client.executeAsync(request, createStringResponseHandler()).get())
                        .hasRootCauseInstanceOf(IOException.class);
                assertThat(awaitLogLines("failed-async", 1))
                        .allSatisfy(columns -> assertThat(columns[RESPONSE_SIZE_COLUMN]).isEqualTo(Integer.toString(TruncatedResponseServer.DELIVERED.length())));
            }
        }
    }

    private HttpClientConfig clientConfig()
    {
        return new HttpClientConfig()
                .setLogEnabled(true)
                .setLogPath(logDirectory.toString())
                // flush every line, so that the log can be read while the client is running
                .setLogFlushInterval(new Duration(0, MILLISECONDS));
    }

    private List<String[]> awaitLogLines(String clientName, int count)
            throws Exception
    {
        Path logFile = logDirectory.resolve(clientName + "-http-client.log");
        // the line is written when Jetty completes the exchange, which may be after the response was handed to the caller
        long deadline = System.nanoTime() + MILLISECONDS.toNanos(10_000);
        while (true) {
            List<String> lines = Files.exists(logFile) ? Files.readAllLines(logFile) : List.of();
            if (lines.size() >= count || System.nanoTime() > deadline) {
                assertThat(lines).hasSize(count);
                return lines.stream()
                        .map(line -> line.split("\t"))
                        .toList();
            }
            Thread.sleep(10);
        }
    }

    private interface RequestExecutor
    {
        void execute(JettyHttpClient client, Request request)
                throws Exception;
    }

    @SuppressWarnings("SocketOpenedButNotSafelyClosed")
    private static final class TruncatedResponseServer
            implements AutoCloseable
    {
        private static final String DELIVERED = "short";

        private final ServerSocket serverSocket;

        TruncatedResponseServer()
                throws IOException
        {
            this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("localhost"));
            Thread acceptor = new Thread(this::run, "TruncatedResponseServer");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private void run()
        {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept();
                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream()) {
                    // wait for the request, so that closing the socket does not reset the connection before the response is read
                    in.read();
                    // Promise 100 bytes, deliver 5, then close to truncate the response body.
                    out.write(("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n" + DELIVERED).getBytes(US_ASCII));
                    out.flush();
                }
                catch (IOException ignored) {
                    // close() causes accept() to throw; isClosed() then exits the loop.
                }
            }
        }

        URI uri()
        {
            return URI.create("http://localhost:" + serverSocket.getLocalPort() + "/");
        }

        @Override
        public void close()
                throws IOException
        {
            serverSocket.close();
        }
    }
}
