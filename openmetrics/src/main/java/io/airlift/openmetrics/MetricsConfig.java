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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class MetricsConfig
{
    private static final Splitter NAME_SPLITTER = Splitter.on('|').trimResults().omitEmptyStrings();

    private List<ObjectName> jmxObjectNames = ImmutableList.of();

    public List<ObjectName> getJmxObjectNames()
    {
        return jmxObjectNames;
    }

    @Config("openmetrics.jmx-object-names")
    @ConfigDescription("JMX object names to include when retrieving all metrics, separated by '|'")
    public MetricsConfig setJmxObjectNames(String names)
    {
        jmxObjectNames = NAME_SPLITTER.splitToStream(names)
                .map(MetricsConfig::toObjectName)
                .collect(toImmutableList());
        return this;
    }

    public MetricsConfig setJmxObjectNames(List<ObjectName> names)
    {
        jmxObjectNames = ImmutableList.copyOf(names);
        return this;
    }

    private static ObjectName toObjectName(String name)
    {
        try {
            return new ObjectName(name);
        }
        catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
