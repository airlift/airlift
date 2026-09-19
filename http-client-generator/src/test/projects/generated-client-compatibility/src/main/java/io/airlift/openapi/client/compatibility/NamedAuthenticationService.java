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
package io.airlift.openapi.client.compatibility;

import io.airlift.api.ApiCustom;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.openapi.OpenApiSecurityRequirement;
import io.airlift.api.openapi.OpenApiSecuritySchemeRequirement;

@ApiService(name = "namedAuthentication", type = CompatibilityServiceType.class, description = "Named authentication operations")
public class NamedAuthenticationService
{
    @ApiCustom(verb = "combined", type = ApiType.GET, description = "Use Basic and API key credentials or bearer authentication", openApiAlternateName = "combined")
    @OpenApiSecurityRequirement({
            @OpenApiSecuritySchemeRequirement(name = "serviceBasic"),
            @OpenApiSecuritySchemeRequirement(name = "serviceKey"),
    })
    @OpenApiSecurityRequirement(@OpenApiSecuritySchemeRequirement(name = "serviceBearer"))
    public NamedStatus combined()
    {
        return new NamedStatus("combined");
    }

    @ApiCustom(verb = "oauth", type = ApiType.GET, description = "Use OAuth client credentials", openApiAlternateName = "oauth")
    @OpenApiSecurityRequirement(@OpenApiSecuritySchemeRequirement(name = "serviceOAuth", scopes = "read"))
    public NamedStatus oauth()
    {
        return new NamedStatus("oauth");
    }

    @ApiCustom(verb = "public", type = ApiType.GET, description = "Use no authentication", openApiAlternateName = "publicOperation")
    @OpenApiSecurityRequirement({})
    public NamedStatus publicOperation()
    {
        return new NamedStatus("public");
    }

    @ApiCustom(verb = "default", type = ApiType.GET, description = "Use default authentication", openApiAlternateName = "defaultOperation")
    public NamedStatus defaultOperation()
    {
        return new NamedStatus("default");
    }

    @ApiResource(name = "namedStatus", description = "Named authentication status")
    public record NamedStatus(String value) {}
}
