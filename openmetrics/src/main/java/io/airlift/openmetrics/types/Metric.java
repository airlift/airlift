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
import com.google.common.collect.ImmutableMap;
import io.airlift.stats.labeled.LabelSet;

import java.util.Map;

public interface Metric
{
    String HELP_LINE_FORMAT = "# HELP %s %s\n";
    String TYPE_LINE_FORMAT = "# TYPE %s %s\n";
    String VALUE_LINE_FORMAT = "%s %s\n";

    String metricName();

    LabelSet labels();

    String getMetricExposition(boolean includeDescriptor);

    default String getMetricExposition()
    {
        return getMetricExposition(true);
    }

    static String formatSingleValuedMetric(String name, String type, String help, ImmutableMap<String, String> labels, String value, boolean includeDescriptor)
    {
        if (includeDescriptor) {
            return TYPE_LINE_FORMAT.formatted(name, type) +
                    (Strings.isNullOrEmpty(help) ? "" : HELP_LINE_FORMAT.formatted(name, help)) +
                    VALUE_LINE_FORMAT.formatted(formatNameWithLabels(name, labels), value);
        }
        return VALUE_LINE_FORMAT.formatted(formatNameWithLabels(name, labels), value);
    }

    static String formatNameWithLabels(String name, ImmutableMap<String, String> labels)
    {
        if (labels.isEmpty()) {
            return name;
        }

        // Assuming each label key and value are 2 characters each, safe under-estimate
        StringBuilder stringBuilder = new StringBuilder(name.length() + 4 * labels.size());
        stringBuilder.append(name).append("{");

        boolean firstLabel = true;
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (!firstLabel) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey()).append("=\"");

            String value = entry.getValue();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> stringBuilder.append("\\\\");
                    case '\"' -> stringBuilder.append("\\\"");
                    case '\n' -> stringBuilder.append("\\n");
                    default -> stringBuilder.append(c);
                }
            }

            stringBuilder.append("\"");
            firstLabel = false;
        }

        return stringBuilder.append("}").toString();
    }
}
