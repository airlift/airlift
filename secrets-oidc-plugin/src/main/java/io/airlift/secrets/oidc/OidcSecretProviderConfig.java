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
package io.airlift.secrets.oidc;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigSecuritySensitive;
import io.airlift.units.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.net.URI;

public class OidcSecretProviderConfig
{
    private String clientId;
    private String clientSecret;
    private URI tokenUrl;
    private String audience;
    private String scopes;
    private Duration cacheExpiry = Duration.valueOf("1h");

    @AssertTrue
    public boolean isHttpsEnabled()
    {
        return tokenUrl != null && (tokenUrl.getScheme().equals("https") || tokenUrl.getHost().equals("localhost") || tokenUrl.getHost().endsWith(".local"));
    }

    @NotNull
    public String getClientId()
    {
        return clientId;
    }

    @Config("client-id")
    @ConfigDescription("Client ID")
    public OidcSecretProviderConfig setClientId(String clientId)
    {
        this.clientId = clientId;
        return this;
    }

    @NotNull
    public String getClientSecret()
    {
        return clientSecret;
    }

    @Config("client-secret")
    @ConfigSecuritySensitive
    @ConfigDescription("Client secret")
    public OidcSecretProviderConfig setClientSecret(String clientSecret)
    {
        this.clientSecret = clientSecret;
        return this;
    }

    @NotNull
    public URI getTokenUrl()
    {
        return tokenUrl;
    }

    @Config("token-url")
    @ConfigDescription("The RFC-8693 token endpoint")
    public OidcSecretProviderConfig setTokenUrl(URI tokenUrl)
    {
        this.tokenUrl = tokenUrl;
        return this;
    }

    @NotNull
    public String getAudience()
    {
        return audience;
    }

    @Config("audience")
    @ConfigDescription("Token audience")
    public OidcSecretProviderConfig setAudience(String audience)
    {
        this.audience = audience;
        return this;
    }

    @NotNull
    public String getScopes()
    {
        return scopes;
    }

    @Config("scopes")
    @ConfigDescription("Token scopes")
    public OidcSecretProviderConfig setScopes(String scopes)
    {
        this.scopes = scopes;
        return this;
    }

    public Duration getCacheExpiry()
    {
        return cacheExpiry;
    }

    @Config("cache-expiry")
    @ConfigDescription("Expiry for the obtained tokens")
    public OidcSecretProviderConfig setCacheExpiry(Duration cacheExpiry)
    {
        this.cacheExpiry = cacheExpiry;
        return this;
    }
}
