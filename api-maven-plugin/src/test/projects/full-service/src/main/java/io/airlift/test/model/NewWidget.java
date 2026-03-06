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
package io.airlift.test.model;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

/**
 * Request body for creating a new widget.
 */
@ApiResource(name = "widget", openApiAlternateName = "newWidget", description = "Request to create a new widget")
public record NewWidget(
        @ApiDescription("Widget name") String name,
        @ApiDescription("Widget description") String description)
{
    public NewWidget
    {
        requireNonNull(name, "name is null");
        requireNonNull(description, "description is null");
    }
}
