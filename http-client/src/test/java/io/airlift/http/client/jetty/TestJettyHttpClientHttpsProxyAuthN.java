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

import static com.google.common.io.Resources.getResource;

public class TestJettyHttpClientHttpsProxyAuthN
        extends AbstractHttpClientTestHttpProxyAuthN
{
    TestJettyHttpClientHttpsProxyAuthN()
    {
        super(getResource("localhost.keystore").toString());
    }

    @Override
    public HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setSecureProxy(true)
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");
    }
}
