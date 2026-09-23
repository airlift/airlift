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
package io.airlift.api.openapi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record OpenApiSecurityMetadata(
        Map<String, SecurityScheme> securitySchemes,
        List<SecurityRequirement> defaultSecurityRequirements)
{
    public OpenApiSecurityMetadata
    {
        securitySchemes = ImmutableMap.copyOf(requireNonNull(securitySchemes, "securitySchemes is null"));
        defaultSecurityRequirements = ImmutableList.copyOf(requireNonNull(defaultSecurityRequirements, "defaultSecurityRequirements is null"));
        securitySchemes.forEach((name, scheme) -> {
            checkArgument(COMPONENT_KEY.matcher(name).matches(), "security scheme name is not a valid OpenAPI component key: %s", name);
            requireNonNull(scheme, "security scheme is null");
        });
        for (SecurityRequirement requirement : defaultSecurityRequirements) {
            validateRequirement(requirement, securitySchemes);
        }
    }

    // RFC 9110 token: the characters allowed in an HTTP field name
    private static final Pattern HTTP_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    // the characters OpenAPI allows in a components map key
    private static final Pattern COMPONENT_KEY = Pattern.compile("[A-Za-z0-9._-]+");

    public sealed interface SecurityScheme
            permits BasicSecurityScheme,
                    BearerSecurityScheme,
                    HeaderApiKeySecurityScheme,
                    OAuth2ClientCredentialsSecurityScheme {}

    public record BearerSecurityScheme(Optional<String> bearerFormat)
            implements SecurityScheme
    {
        public BearerSecurityScheme
        {
            bearerFormat = requireNonNull(bearerFormat, "bearerFormat is null");
            bearerFormat.ifPresent(value -> checkArgument(!value.isBlank(), "bearerFormat is blank"));
        }

        public BearerSecurityScheme()
        {
            this(Optional.empty());
        }

        public BearerSecurityScheme(String bearerFormat)
        {
            this(Optional.of(bearerFormat));
        }
    }

    public record BasicSecurityScheme()
            implements SecurityScheme {}

    public record HeaderApiKeySecurityScheme(String headerName)
            implements SecurityScheme
    {
        public HeaderApiKeySecurityScheme
        {
            checkArgument(!requireNonNull(headerName, "headerName is null").isBlank(), "headerName is blank");
            checkArgument(HTTP_TOKEN.matcher(headerName).matches(), "headerName is not a valid HTTP header name: %s", headerName);
        }
    }

    public record OAuth2ClientCredentialsSecurityScheme(
            String tokenUrl,
            Map<String, String> scopes,
            TokenEndpointAuthenticationMethod tokenEndpointAuthenticationMethod)
            implements SecurityScheme
    {
        public OAuth2ClientCredentialsSecurityScheme
        {
            checkArgument(!requireNonNull(tokenUrl, "tokenUrl is null").isBlank(), "tokenUrl is blank");
            scopes = ImmutableMap.copyOf(requireNonNull(scopes, "scopes is null"));
            scopes.forEach((name, description) -> {
                checkArgument(!requireNonNull(name, "scope name is null").isBlank(), "scope name is blank");
                requireNonNull(description, "scope description is null");
            });
            requireNonNull(tokenEndpointAuthenticationMethod, "tokenEndpointAuthenticationMethod is null");
        }
    }

    public enum TokenEndpointAuthenticationMethod
    {
        CLIENT_SECRET_BASIC
    }

    public record SecurityRequirement(Map<String, List<String>> schemes)
    {
        public SecurityRequirement
        {
            ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();
            requireNonNull(schemes, "schemes is null").forEach((name, scopes) -> {
                checkArgument(!requireNonNull(name, "scheme name is null").isBlank(), "scheme name is blank");
                ImmutableList<String> immutableScopes = ImmutableList.copyOf(requireNonNull(scopes, "scopes is null"));
                immutableScopes.forEach(scope -> checkArgument(!scope.isBlank(), "scope is blank"));
                builder.put(name, immutableScopes);
            });
            schemes = builder.buildOrThrow();
        }
    }

    static void validateRequirement(SecurityRequirement requirement, Map<String, SecurityScheme> schemes)
    {
        requireNonNull(requirement, "security requirement is null").schemes().forEach((name, scopes) -> {
            SecurityScheme scheme = schemes.get(name);
            checkArgument(scheme != null, "security requirement references unknown scheme: %s", name);
            checkArgument(scopes.isEmpty() || scheme instanceof OAuth2ClientCredentialsSecurityScheme, "scopes require an OAuth2 scheme: %s", name);
            if (scheme instanceof OAuth2ClientCredentialsSecurityScheme oauth2) {
                // generated clients request exactly the declared scopes, so an undeclared scope cannot be honored
                scopes.forEach(scope -> checkArgument(oauth2.scopes().containsKey(scope), "security requirement references undeclared scope for scheme %s: %s", name, scope));
            }
        });
    }
}
