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
package io.airlift.configuration;

import com.google.common.collect.Iterators;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class TomlConfiguration
{
    public static TomlConfiguration createTomlConfiguration(File file)
    {
        checkArgument(file.getName().endsWith(".toml"), "TOML files must end with '.toml'");
        try {
            return new TomlConfiguration(Toml.parse(file.toPath()));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final TomlTable tomlTable;

    public TomlConfiguration(TomlTable tomlTable)
    {
        this.tomlTable = requireNonNull(tomlTable, "tomlTable is null");
    }

    public Map<String, String> getParentConfiguration()
    {
        return this.tomlTable.entryPathSet().stream()
                .filter(entry -> entry.getKey().size() == 1)
                .collect(toImmutableMap(
                        entry -> Iterators.getOnlyElement(entry.getKey().iterator()),
                        entry -> tomlValueToString(entry.getValue())));
    }

    public Set<String> getNamespaces()
    {
        return this.tomlTable.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof TomlTable)
                .map(Entry::getKey)
                .collect(toImmutableSet());
    }

    public Map<String, String> getNamespaceConfiguration(String namespace)
    {
        checkArgument(tomlTable.contains(namespace), "Namespace %s not found", namespace);
        return this.tomlTable.getTable(namespace).dottedEntrySet().stream()
                .collect(toImmutableMap(Entry::getKey, entry -> tomlValueToString(entry.getValue())));
    }

    private static String tomlValueToString(Object object)
    {
        if (object instanceof TomlArray tomlArray) {
            return tomlArray.toList().stream()
                    .map(TomlConfiguration::tomlValueToString)
                    .collect(joining(","));
        }
        return object.toString();
    }
}
