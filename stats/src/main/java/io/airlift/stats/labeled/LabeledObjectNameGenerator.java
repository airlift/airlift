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
package io.airlift.stats.labeled;

import org.weakref.jmx.ObjectNameBuilder;
import org.weakref.jmx.ObjectNameGenerator;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public class LabeledObjectNameGenerator
        implements ObjectNameGenerator
{
    private final String metricName;

    public LabeledObjectNameGenerator(String metricName)
    {
        this.metricName = requireNonNull(metricName, "metricName is null");
    }

    @Override
    public String generatedNameOf(Class<?> type, Map<String, String> properties)
    {
        return new ObjectNameBuilder(metricName)
                .withProperties(properties)
                .build();
    }

    public String generatedNameOf(Map<String, String> properties)
    {
        // type ignored, metric name is passed through constructor not derived from type
        return generatedNameOf(Void.class, properties);
    }
}
