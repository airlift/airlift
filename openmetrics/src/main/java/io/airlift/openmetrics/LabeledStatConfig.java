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

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class LabeledStatConfig
{
    private int labeledStatMaxCardinality = 10_000;

    @Min(1)
    @Max(1_000_000)
    public int getLabeledStatMaxCardinality()
    {
        return labeledStatMaxCardinality;
    }

    @Config("labeled-stat.max-cardinality")
    @ConfigDescription("After max cardinality for a single labeled stat is reached, emits warn logs and stops creating new labeled stat values for that labeled stat")
    public LabeledStatConfig setLabeledStatMaxCardinality(int maxCardinality)
    {
        this.labeledStatMaxCardinality = maxCardinality;
        return this;
    }
}
