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
package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.util.List;

public class MetricsConfig
{
    private List<ObjectName> jmxObjectNames = ImmutableList.of();

    public List<ObjectName> getJmxObjectNames()
    {
        return jmxObjectNames;
    }

    @Config("openmetrics.jmx-object-names")
    @ConfigDescription("JMX object names to include when retrieving all metrics.")
    public MetricsConfig setJmxObjectNames(List<String> jmxObjectNames)
    {
        ImmutableList.Builder<ObjectName> objectNames = ImmutableList.builder();

        for (String jmxObjectName : jmxObjectNames) {
            try {
                objectNames.add(new ObjectName(jmxObjectName));
            }
            catch (MalformedObjectNameException e) {
                throw new RuntimeException(e);
            }
        }

        this.jmxObjectNames = objectNames.build();
        return this;
    }
}
