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
import io.airlift.http.client.TestingHttpProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.proxy.ProxyServlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.airlift.http.client.HttpStatus.PROXY_AUTHENTIATION_REQUIRED;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHENTICATE;
import static org.eclipse.jetty.http.HttpHeader.PROXY_AUTHORIZATION;

public abstract class AbstractHttpClientTestHttpProxyAuthN
        extends AbstractHttpClientTestHttpProxy
{
    private final String proxyUser = "proxy_user";
    private final String proxyPass = "proxy_pass";

    protected AbstractHttpClientTestHttpProxyAuthN() {}

    protected AbstractHttpClientTestHttpProxyAuthN(String keystore)
    {
        super(keystore);
    }

    @Override
    public HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttpProxyUser(proxyUser)
                .setHttpProxyPassword(proxyPass);
    }

    @Override
    protected TestingHttpProxy createTestingHttpProxy()
            throws Exception
    {
        return new TestingHttpProxy(keystore, new ProxyAuthNServlet(proxyUser, proxyPass));
    }

    static class ProxyAuthNServlet
            extends ProxyServlet
    {
        private final String credentials;

        public ProxyAuthNServlet(String user, String password)
        {
            this.credentials = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.ISO_8859_1));
        }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException
        {
            String authorization = req.getHeader(PROXY_AUTHORIZATION.asString());
            if (authorization == null) {
                resp.setStatus(PROXY_AUTHENTIATION_REQUIRED.code());
                resp.setHeader(PROXY_AUTHENTICATE.asString(), "Basic realm=\"Proxy Realm\"");
                return;
            }

            String prefix = "Basic ";
            if (authorization.startsWith(prefix)) {
                String attempt = authorization.substring(prefix.length());
                if (credentials.equals(attempt)) {
                    super.service(req, resp);
                }
                else {
                    resp.setStatus(SC_FORBIDDEN);
                }
            }
        }
    }
}
