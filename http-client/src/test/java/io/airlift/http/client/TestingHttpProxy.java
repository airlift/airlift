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
package io.airlift.http.client;

import com.google.common.net.HostAndPort;
import jakarta.servlet.Servlet;
import org.eclipse.jetty.ee10.proxy.ProxyServlet;
import org.eclipse.jetty.server.handler.ConnectHandler;

import java.util.Optional;

public class TestingHttpProxy
        implements AutoCloseable
{
    private final TestingHttpServer server;

    public TestingHttpProxy(Optional<String> keystore)
            throws Exception
    {
        this(keystore, new ProxyServlet());
    }

    public TestingHttpProxy(Optional<String> keystore, Servlet servlet)
            throws Exception
    {
        this.server = new TestingHttpServer(
                keystore,
                servlet,
                config -> config.setSendDateHeader(false),
                Optional.of(new ConnectHandler()));
    }

    public HostAndPort getHostAndPort()
    {
        return server.getHostAndPort();
    }

    @Override
    public void close()
            throws Exception
    {
        server.close();
    }
}
