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

import static java.util.Objects.requireNonNull;

public record Gauge(String metricName, double value, String help)
        implements Metric
{
    public Gauge(String metricName, double value, String help)
    {
        this.metricName = requireNonNull(metricName, "metricName is null");
        this.value = value;
        this.help = help;
    }

    public static Gauge from(String metricName, Number value, String help)
    {
        return new Gauge(metricName, value.doubleValue(), help);
    }

    @Override
    public String getMetricExposition()
    {
        return Metric.formatSingleValuedMetric(metricName, "gauge", help, Double.toString(value));
    }
}
