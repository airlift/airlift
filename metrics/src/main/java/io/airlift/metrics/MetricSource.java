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
package io.airlift.metrics;

import javax.management.ObjectName;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface MetricSource
{
    record JmxMetricSource(ObjectName name)
            implements MetricSource
    {
        public JmxMetricSource
        {
            requireNonNull(name, "name is null");
        }
    }

    record ManagedMetricSource(String name, Optional<Class<?>> exportedType, Optional<String> originalName, Map<String, String> originalProperties)
            implements MetricSource
    {
        public ManagedMetricSource(String name)
        {
            this(name, Optional.empty(), Optional.empty(), Map.of());
        }

        public ManagedMetricSource
        {
            requireNonNull(name, "name is null");
            requireNonNull(exportedType, "exportedType is null");
            requireNonNull(originalName, "originalName is null");
            originalProperties = Map.copyOf(requireNonNull(originalProperties, "originalProperties is null"));
        }
    }
}
