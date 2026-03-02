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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.stats.labeled.LabelSet.EMPTY;
import static io.airlift.stats.labeled.LabelSet.LABEL_SET_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestLabelSet
{
    @Test
    public void testInvalidLabels()
    {
        assertThat(assertThrows(IllegalArgumentException.class, () -> LabelSet.fromLabels(ImmutableMap.of("white space", "value"))))
                .hasMessage("Invalid label name: white space, must match regex [a-zA-Z_][a-zA-Z0-9_.]*");

        assertThat(assertThrows(IllegalArgumentException.class, () -> LabelSet.fromLabels(ImmutableMap.of("11startsWithDigit", "value"))))
                .hasMessage("Invalid label name: 11startsWithDigit, must match regex [a-zA-Z_][a-zA-Z0-9_.]*");

        assertThat(assertThrows(IllegalArgumentException.class, () -> LabelSet.fromLabels(ImmutableMap.of("\uD83D\uDE00", "value"))))
                .hasMessage("Invalid label name: \uD83D\uDE00, must match regex [a-zA-Z_][a-zA-Z0-9_.]*");
    }

    @Test
    public void testLabelCanonicalization()
    {
        LabelSet labelSet = LabelSet.fromLabels(ImmutableMap.of(
                "yy", "2",
                "xx", "ab",
                "aa", "bb",
                "zz", "1"));

        List<Map.Entry<String, String>> labelValues = labelSet.asMap()
                .entrySet()
                .stream()
                .collect(toImmutableList());
        assertThat(labelValues).containsExactly(
                Map.entry("aa", "bb"),
                Map.entry("xx", "ab"),
                Map.entry("yy", "2"),
                Map.entry("zz", "1"));
    }

    @Test
    public void testLabelSetSorting()
    {
        Set<LabelSet> labelSets = ImmutableSet.of(
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb")),
                LabelSet.fromLabels(ImmutableMap.of("zz", "aa")),
                LabelSet.fromLabels(ImmutableMap.of("yy", "bb")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "aa")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "ab", "yy", "1")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "ab")),
                EMPTY);

        assertThat(labelSets.stream().sorted(LABEL_SET_COMPARATOR)).containsExactly(
                EMPTY,
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "aa")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "ab")),
                LabelSet.fromLabels(ImmutableMap.of("aa", "bb", "xx", "ab", "yy", "1")),
                LabelSet.fromLabels(ImmutableMap.of("yy", "bb")),
                LabelSet.fromLabels(ImmutableMap.of("zz", "aa")));
    }
}
