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

import io.airlift.api.ApiGet;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiHeader;
import io.airlift.api.ApiParameter;
import io.airlift.api.ApiPatch;
import io.airlift.api.ApiService;
import io.airlift.api.ApiStreamResponse.ApiServerSentEventStreamResponse;
import io.airlift.api.ApiType;
import io.airlift.api.openapi.OpenApiIdempotencyKey;

import java.nio.charset.StandardCharsets;

@ApiService(name = "compatibility", type = CompatibilityServiceType.class, description = "Generated client compatibility operations")
public class StatusService
{
    @ApiGet(description = "Return service status")
    public ServiceStatus getStatus()
    {
        return new ServiceStatus(FrameKind.TEXT);
    }

    @ApiCustom(verb = "safe-execute", type = ApiType.CREATE, description = "Execute an idempotent operation", responses = CompatibilityConflict.class, openApiAlternateName = "safeExecute")
    @OpenApiIdempotencyKey(header = "Idempotency-Key")
    public void safeExecute(@ApiParameter(name = "Idempotency-Key") ApiHeader idempotencyKey) {}

    @ApiCustom(verb = "unsafe-execute", type = ApiType.CREATE, description = "Execute a non-idempotent operation", responses = CompatibilityConflict.class, openApiAlternateName = "unsafeExecute")
    public void unsafeExecute() {}

    @ApiCustom(verb = "read-failure", type = ApiType.GET, description = "Read a possible failure", responses = CompatibilityConflict.class, openApiAlternateName = "readFailure")
    public ServiceStatus readFailure()
    {
        return getStatus();
    }

    @ApiCreate(description = "Stream service status events", openApiAlternateName = "streamEvents")
    public ApiServerSentEventStreamResponse<ServiceStatus, CompatibilityEvent> streamEvents()
    {
        return new ApiServerSentEventStreamResponse<>(outputStream -> {
            try {
                outputStream.write("""
                        : keepalive\r
                        id: status-1\r
                        event: status\r
                        retry: 1500\r
                        data: {"message":"héllo",\r
                        data: "kind":"TEXT"}\r
                        \r
                        : complete\r
                        """.getBytes(StandardCharsets.UTF_8));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @ApiCustom(verb = "patch-status", type = ApiType.UPDATE, description = "Patch service status", responses = CompatibilityConflict.class, openApiAlternateName = "patchStatus")
    public void patchStatus(ApiPatch<ServiceStatus> patch) {}
}
