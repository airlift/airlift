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

import java.io.IOException;
import java.io.Writer;

public sealed interface Metric
        permits BigCounter,
                CompositeMetric,
                Counter,
                Gauge,
                Info,
                Summary
{
    String metricName();

    /**
     * Writes this metric's sample lines in the OpenMetrics text exposition format,
     * e.g. {@code name{label="value"} 42\n}. The type and help descriptor lines are
     * not written here; they come from {@link #writeMetricDescriptor(Writer writer)} and are emitted
     * once per metric family by the caller.
     * <p>
     * The output goes to the response stream verbatim — the caller performs no
     * sanitization or validation. Since the format is line-oriented, implementations
     * are responsible for keeping the stream well-formed: every sample line must end
     * with exactly one {@code \n}, and no other line break may reach the writer.
     * Free-form text such as label values must have {@code \\}, {@code "} and
     * {@code \n} escaped per the OpenMetrics escaping rules (see {@link io.airlift.openmetrics.MetricsUtils#renderLabels}),
     * and {@code \r} must never be written as it is not permitted by the format in any
     * form, escaped or raw. A stray {@code \n} or {@code \r} corrupts not just this
     * metric but the parse of every line that follows.
     *
     * @param writer destination for the exposition; buffered by the caller, so
     *         implementations may issue many small writes
     */
    void writeMetricExposition(Writer writer)
            throws IOException;

    void writeMetricDescriptor(Writer writer)
            throws IOException;
}
