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

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Wrapper around a sorted ImmutableMap of labels
 * Provides deterministic ordering and iteration of labels, as well as equals and hashCode
 */
public class LabelSet
{
    // suggested regex, with additional character ".": https://prometheus.io/docs/concepts/data_model/
    private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");
    public static final LabelSet EMPTY = new LabelSet(ImmutableMap.of());
    public static final Comparator<LabelSet> LABEL_SET_COMPARATOR = Comparator.comparing(
            (LabelSet labelSet) -> labelSet.asMap().entrySet(),
            Comparators.lexicographical(Map.Entry.<String, String>comparingByKey().thenComparing(Map.Entry.comparingByValue())));

    private final ImmutableMap<String, String> canonicalLabels;

    public static LabelSet fromLabels(Map<String, String> labels)
    {
        return new LabelSet(labels);
    }

    private LabelSet(Map<String, String> labels)
    {
        this.canonicalLabels = canonicalize(labels);
    }

    public ImmutableMap<String, String> asMap()
    {
        return canonicalLabels;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LabelSet labelSet = (LabelSet) o;
        return canonicalLabels.equals(labelSet.canonicalLabels);
    }

    @Override
    public int hashCode()
    {
        return canonicalLabels.hashCode();
    }

    @Override
    public String toString()
    {
        return canonicalLabels.toString();
    }

    private static ImmutableMap<String, String> canonicalize(Map<String, String> labels)
    {
        if (labels.isEmpty()) {
            return ImmutableMap.of();
        }

        String[] keys = labels.keySet().toArray(new String[labels.size()]);
        Arrays.sort(keys);
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builderWithExpectedSize(keys.length);
        for (String key : keys) {
            validateLabelName(key);
            builder.put(key, labels.get(key));
        }

        return builder.buildOrThrow();
    }

    private static void validateLabelName(String labelName)
    {
        requireNonNull(labelName, "labelName is null");
        checkArgument(LABEL_NAME_PATTERN.matcher(labelName).matches(), "Invalid label name: %s, must match regex %s", labelName, LABEL_NAME_PATTERN.pattern());
    }
}
