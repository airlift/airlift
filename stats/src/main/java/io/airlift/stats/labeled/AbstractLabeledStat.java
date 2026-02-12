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

import com.google.common.util.concurrent.RateLimiter;
import io.airlift.log.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public abstract class AbstractLabeledStat<T extends AbstractLabeledStat.LabeledStat>
{
    private static final Logger log = Logger.get(AbstractLabeledStat.class);
    // suggested regex, with additional character ".": https://prometheus.io/docs/concepts/data_model/
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:.]*");
    // max one warning log per minute per metric
    private final RateLimiter warningRateLimiter = RateLimiter.create(1.0 / 60.0);

    protected final LabeledStatRegistry labeledStatRegistry;
    protected final String metricName;
    protected final LabeledObjectNameGenerator objectNameGenerator;
    protected final String description;
    protected final ConcurrentHashMap<LabelSet, T> labeledStats;

    public AbstractLabeledStat(LabeledStatRegistry labeledStatRegistry, String metricName, String description)
    {
        this.labeledStatRegistry = requireNonNull(labeledStatRegistry, "labeledStatRegistry is null");
        this.metricName = validateMetricName(metricName);
        this.objectNameGenerator = new LabeledObjectNameGenerator(metricName);
        this.description = requireNonNull(description, "description is null");
        this.labeledStats = new ConcurrentHashMap<>();
    }

    protected abstract T createStat(LabelSet labels);

    protected T getOrCreate(LabelSet labelSet)
    {
        // fast path for existing labelsets
        T existingStat = labeledStats.get(labelSet);
        if (existingStat != null) {
            return existingStat;
        }
        if (labeledStats.size() >= labeledStatRegistry.getLabeledStatMaxCardinality() && warningRateLimiter.tryAcquire()) {
            log.warn("High cardinality detected for metric %s: %d unique labelsets", metricName, labeledStats.size());
        }
        return labeledStats.computeIfAbsent(labelSet, l -> {
            T stat = createStat(labelSet);
            try {
                labeledStatRegistry.getMBeanExporter().export(objectNameGenerator.generatedNameOf(l.asMap()), stat);
            }
            catch (Exception e) {
                log.error(e, "Failed to register MBean for labels %s", l);
            }
            return stat;
        });
    }

    public static String validateMetricName(String metricName)
    {
        requireNonNull(metricName, "metricName is null");
        checkArgument(METRIC_NAME_PATTERN.matcher(metricName).matches(), "Invalid metric name: %s, must match regex %s", metricName, METRIC_NAME_PATTERN.pattern());
        return metricName;
    }

    abstract static class LabeledStat
    {
        private final String metricName;
        private final String description;
        private final LabelSet labels;

        LabeledStat(String metricName, String description, LabelSet labels)
        {
            this.metricName = requireNonNull(metricName, "metricName is null");
            this.description = requireNonNull(description, "description is null");
            this.labels = requireNonNull(labels, "labels is null");
        }

        public String metricName()
        {
            return metricName;
        }

        public String description()
        {
            return description;
        }

        public LabelSet labels()
        {
            return labels;
        }
    }
}
