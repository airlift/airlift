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
package io.airlift.test.context;

import io.airlift.api.ApiCreate;
import io.airlift.api.ApiGet;
import io.airlift.api.ApiService;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import java.util.UUID;

@ApiService(name = "widgets", type = ContextTestServiceType.class, description = "Service with container-supplied parameters")
public class ContextService
{
    @ApiGet(description = "Get a widget")
    public Widget getWidget(@Injected UUID requestId, @Context UriInfo uriInfo)
    {
        return null;
    }

    @ApiCreate(description = "Create a widget")
    public Widget createWidget(@Injected UUID requestId, NewWidget newWidget)
    {
        return null;
    }
}
