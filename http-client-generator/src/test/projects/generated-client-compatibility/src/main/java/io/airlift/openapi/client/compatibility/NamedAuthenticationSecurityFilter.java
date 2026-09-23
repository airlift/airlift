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

import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.openapi.OpenApiExtensionFilter;
import io.airlift.api.openapi.models.Operation;
import io.airlift.api.openapi.models.SecurityRequirement;

import java.util.List;

/**
 * Publishes the security of {@link NamedAuthenticationService} operations that differ from the default requirement.
 */
public class NamedAuthenticationSecurityFilter
        implements OpenApiExtensionFilter
{
    @Override
    public Operation apply(ModelService modelService, ModelMethod modelMethod, Operation operation)
    {
        if (modelMethod.method().getDeclaringClass() != NamedAuthenticationService.class) {
            return operation;
        }
        return switch (modelMethod.method().getName()) {
            case "combined" -> operation.security(List.of(
                    new SecurityRequirement().addList("serviceBasic").addList("serviceKey"),
                    new SecurityRequirement().addList("serviceBearer")));
            case "oauth" -> operation.security(List.of(new SecurityRequirement().addList("serviceOAuth", List.of("read"))));
            case "publicOperation" -> operation.security(List.of(new SecurityRequirement()));
            default -> operation;
        };
    }
}
