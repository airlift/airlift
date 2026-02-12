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

import com.google.common.util.concurrent.AtomicDouble;
import com.google.errorprone.annotations.ThreadSafe;

@ThreadSafe
public class LabeledGaugeStat
        extends AbstractLabeledStat<LabeledGaugeStat.GaugeStat>
{
    LabeledGaugeStat(LabeledStatRegistry labeledStatRegistry, String metricName, String description)
    {
        super(labeledStatRegistry, metricName, description);
    }

    @Override
    protected GaugeStat createStat(LabelSet labels)
    {
        return new GaugeStat(metricName, description, labels);
    }

    public void set(LabelSet labels, double value)
    {
        getOrCreate(labels).setValue(value);
    }

    @ThreadSafe
    public static class GaugeStat
            extends LabeledStat
    {
        private final AtomicDouble value;

        public GaugeStat(String metricName, String description, LabelSet labels)
        {
            super(metricName, description, labels);
            this.value = new AtomicDouble();
        }

        public void setValue(double v)
        {
            value.set(v);
        }

        public double getValue()
        {
            return value.get();
        }
    }
}
