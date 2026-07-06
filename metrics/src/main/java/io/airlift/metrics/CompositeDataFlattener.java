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
package io.airlift.metrics;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class CompositeDataFlattener
{
    private CompositeDataFlattener() {}

    public interface LeafConsumer
    {
        void accept(String name, Map<String, String> labels, Object value);
    }

    public static void flatten(String name, Object value, Map<String, String> labels, String separator, UnaryOperator<String> itemNameMapper, LeafConsumer consumer)
    {
        switch (value) {
            case CompositeData compositeData -> {
                for (String itemName : compositeData.getCompositeType().keySet()) {
                    flatten(name + separator + itemNameMapper.apply(itemName), compositeData.get(itemName), labels, separator, itemNameMapper, consumer);
                }
            }
            case TabularData tabularData -> {
                if (tabularData.isEmpty()) {
                    return;
                }
                Set<String> indexNames = ImmutableSet.copyOf(tabularData.getTabularType().getIndexNames());
                for (Object row : tabularData.values()) {
                    if (!(row instanceof CompositeData compositeData)) {
                        continue;
                    }
                    Map<String, String> rowLabels = new HashMap<>(labels);
                    for (String indexName : indexNames) {
                        if (compositeData.containsKey(indexName)) {
                            rowLabels.put(indexName, compositeData.get(indexName).toString());
                        }
                    }
                    for (String itemName : Sets.difference(compositeData.getCompositeType().keySet(), indexNames)) {
                        flatten(name + separator + itemNameMapper.apply(itemName), compositeData.get(itemName), rowLabels, separator, itemNameMapper, consumer);
                    }
                }
            }
            case null, default -> consumer.accept(name, labels, value);
        }
    }
}
