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

import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.StatusResponseHandler.StatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestJettyHttpClientResponseTruncation
{
    @Test
    @Timeout(30)
    public void testTruncatedResponseRoutedToHandleException()
            throws Exception
    {
        AtomicBoolean handleExceptionCalled = new AtomicBoolean();
        try (TruncatedResponseServer server = new TruncatedResponseServer();
                JettyHttpClient client = new JettyHttpClient(new HttpClientConfig())) {
            Request request = prepareGet()
                    .setUri(server.uri())
                    .build();
            assertThatThrownBy(() -> client.executeAsync(request, trackingHandler(handleExceptionCalled)).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
        }
        assertThat(handleExceptionCalled).isTrue();
    }

    private static ResponseHandler<StatusResponse, RuntimeException> trackingHandler(AtomicBoolean handleExceptionCalled)
    {
        ResponseHandler<StatusResponse, RuntimeException> delegate = createStatusResponseHandler();
        return new ResponseHandler<>()
        {
            @Override
            public StatusResponse handleException(Request request, Exception exception)
            {
                handleExceptionCalled.set(true);
                throw new RuntimeException(exception);
            }

            @Override
            public StatusResponse handle(Request request, Response response)
            {
                return delegate.handle(request, response);
            }
        };
    }

    @SuppressWarnings("SocketOpenedButNotSafelyClosed")
    private static final class TruncatedResponseServer
            implements AutoCloseable
    {
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
                        OutputStream out = socket.getOutputStream()) {
                    // Promise 100 bytes, deliver 5, then close to truncate the response body.
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nshort".getBytes(US_ASCII));
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
