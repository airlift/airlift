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

import com.google.errorprone.annotations.ThreadSafe;

import java.util.concurrent.atomic.LongAdder;

@ThreadSafe
public class LabeledCounterStat
        extends AbstractLabeledStat<LabeledCounterStat.Value>
{
    LabeledCounterStat(LabeledStatRegistry labeledStatRegistry, String metricName, String description)
    {
        super(labeledStatRegistry, metricName, description);
    }

    @Override
    protected Value createStat(LabelSet labels)
    {
        return new Value(metricName, description, labels);
    }

    public void increment(LabelSet labelSet)
    {
        Value counterValue = getOrCreate(labelSet);
        if (counterValue != null) {
            counterValue.increment();
        }
    }

    @ThreadSafe
    public static class Value
            extends LabeledStat
    {
        private final LongAdder counter;

        public Value(String metricName, String description, LabelSet labels)
        {
            super(metricName, description, labels);
            this.counter = new LongAdder();
        }

        public void increment()
        {
            counter.increment();
        }

        public long getCount()
        {
            return counter.sum();
        }
    }
}
