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

import io.airlift.stats.CounterStat;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record Counter(String metricName, long value, Map<String, String> labels, String help)
        implements Metric
{
    public Counter(String metricName, long value, Map<String, String> labels, String help)
    {
        this.metricName = requireNonNull(metricName, "metricName is null");
        this.value = value;
        this.labels = labels;
        this.help = help;
    }

    public static Counter from(String metricName, CounterStat counterStat, Map<String, String> labels, String help)
    {
        return new Counter(metricName, counterStat.getTotalCount(), labels, help);
    }

    @Override
    public String getMetricExposition()
    {
        return Metric.formatSingleValuedMetric(metricName, "counter", help, labels, Long.toString(value));
    }
}
