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

import io.github.wasabithumb.jtoml.key.TomlKey;
import io.github.wasabithumb.jtoml.value.TomlValue;
import io.github.wasabithumb.jtoml.value.array.TomlArray;
import io.github.wasabithumb.jtoml.value.table.TomlTable;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.github.wasabithumb.jtoml.JToml.jToml;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class TomlConfiguration
{
    public static TomlConfiguration createTomlConfiguration(File file)
    {
        checkArgument(file.getName().endsWith(".toml"), "TOML files must end with '.toml'");
        return new TomlConfiguration(jToml().read(file));
    }

    private final TomlTable tomlTable;

    public TomlConfiguration(TomlTable tomlTable)
    {
        this.tomlTable = requireNonNull(tomlTable, "tomlTable is null");
    }

    public Map<String, String> getParentConfiguration()
    {
        return tomlTable.keys().stream()
                .filter(key -> key.size() == 1)
                .collect(toImmutableMap(
                        TomlKey::toString,
                        key -> tomlValueToString(tomlTable.get(key))));
    }

    public Set<String> getNamespaces()
    {
        return tomlTable.keys(false).stream()
                .filter(key -> tomlTable.get(key).isTable())
                .map(TomlKey::toString)
                .collect(toImmutableSet());
    }

    public Map<String, String> getNamespaceConfiguration(String namespace)
    {
        checkArgument(tomlTable.contains(namespace), "Namespace %s not found", namespace);
        TomlValue namespaceValue = tomlTable.get(namespace);
        checkArgument(namespaceValue.isTable(), "Namespace %s is not a TOML table", namespace);
        TomlTable namespaceTable = namespaceValue.asTable();
        return namespaceTable.keys().stream()
                .collect(toImmutableMap(
                        TomlKey::toString,
                        key -> tomlValueToString(namespaceTable.get(key))));
    }

    private static String tomlValueToString(TomlValue value)
    {
        if (value instanceof TomlArray array) {
            return StreamSupport.stream(array.spliterator(), false)
                    .map(TomlConfiguration::tomlValueToString)
                    .collect(joining(","));
        }
        return value.asPrimitive().value().toString();
    }
}
