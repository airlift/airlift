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

public interface Metric
{
    String HELP_LINE_FORMAT = "# HELP %s %s\n";
    String TYPE_LINE_FORMAT = "# TYPE %s %s\n";
    String VALUE_LINE_FORMAT = "%s %s\n";

    String metricName();

    String getMetricExposition();

    static String formatSingleValuedMetric(String name, String type, String help, String value)
    {
        return TYPE_LINE_FORMAT.formatted(name, type) +
                (Strings.isNullOrEmpty(help) ? "" : HELP_LINE_FORMAT.formatted(name, help)) +
                VALUE_LINE_FORMAT.formatted(name, value);
    }
}
