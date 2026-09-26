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
package io.airlift.test;

import io.airlift.api.model.ModelMethod;
import io.airlift.api.model.ModelService;
import io.airlift.api.openapi.OpenApiExtensionFilter;
import io.airlift.api.openapi.models.Operation;

public class OperationNameExtensionFilter
        implements OpenApiExtensionFilter
{
    @Override
    public Operation apply(ModelService modelService, ModelMethod modelMethod, Operation operation)
    {
        operation.addExtension("x-operation-name", modelMethod.method().getName());
        return operation;
    }
}
