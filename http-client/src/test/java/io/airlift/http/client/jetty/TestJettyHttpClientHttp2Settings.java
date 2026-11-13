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
import io.airlift.units.DataSize;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.Frame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.SettingsFrame;
import org.eclipse.jetty.http2.server.RawHTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class TestJettyHttpClientHttp2Settings
{
    @Test
    public void testMaxFrameSize()
            throws Exception
    {
        assertThat(advertisedMaxFrameSize(new HttpClientConfig())).isEqualTo(16 * 1024);
        assertThat(advertisedMaxFrameSize(new HttpClientConfig().setHttp2MaxFrameSize(DataSize.of(128, KILOBYTE)))).isEqualTo(128 * 1024);
    }

    /**
     * Returns the frame size limit that a client with the given configuration announces to an HTTP/2 server.
     */
    private static int advertisedMaxFrameSize(HttpClientConfig config)
            throws Exception
    {
        CompletableFuture<Map<Integer, Integer>> settings = new CompletableFuture<>();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, new RawHTTP2ServerConnectionFactory(new ServerSessionListener()
        {
            @Override
            public void onSettings(Session session, SettingsFrame frame)
            {
                settings.complete(frame.getSettings());
            }

            @Override
            public Stream.Listener onNewStream(Stream stream, HeadersFrame frame)
            {
                MetaData.Response response = new MetaData.Response(HttpStatus.OK_200, null, HttpVersion.HTTP_2, HttpFields.EMPTY);
                stream.headers(new HeadersFrame(stream.getId(), response, null, true), Callback.NOOP);
                return null;
            }
        }));
        connector.setHost("127.0.0.1");
        server.addConnector(connector);
        server.start();
        try (JettyHttpClient client = new JettyHttpClient("test", config.setHttp2Enabled(true))) {
            Request request = prepareGet()
                    .setUri(URI.create("http://127.0.0.1:" + connector.getLocalPort()))
                    .build();
            assertThat(client.execute(request, createStatusResponseHandler()).getStatusCode()).isEqualTo(200);
            // a client leaves out a setting that has the default value of the protocol
            return settings.get(10, SECONDS).getOrDefault(SettingsFrame.MAX_FRAME_SIZE, Frame.DEFAULT_MAX_SIZE);
        }
        finally {
            server.stop();
        }
    }
}
