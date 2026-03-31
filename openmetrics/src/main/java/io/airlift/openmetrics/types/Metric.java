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
package io.airlift.openmetrics.types;

import com.google.common.base.Strings;

import java.util.Map;
import java.util.stream.Collectors;

public sealed interface Metric
        permits BigCounter, CompositeMetric, Counter, Gauge, Info, Summary
{
    String metricName();

    String getMetricExposition();

    String getMetricDescriptor();

    static String formatSingleValuedMetric(String name, Map<String, String> labels, String value)
    {
        return formatNameWithLabels(name, labels) + ' ' + value + '\n';
    }

    static String formatMetricDescriptor(String metricName, String type, String help)
    {
        StringBuilder output = new StringBuilder();
        output.append("# TYPE ")
                .append(metricName)
                .append(' ')
                .append(type)
                .append('\n');
        if (!Strings.isNullOrEmpty(help)) {
            output.append("# HELP ")
                    .append(metricName)
                    .append(' ')
                    .append(help)
                    .append('\n');
        }
        return output.toString();
    }

    static String formatNameWithLabels(String name, Map<String, String> labels)
    {
        if (labels.isEmpty()) {
            return name;
        }
        return name + labels.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
