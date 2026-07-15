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
package io.airlift.yaml;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.yaml.YamlCodec.listYamlCodec;
import static io.airlift.yaml.YamlCodec.mapYamlCodec;
import static io.airlift.yaml.YamlCodec.yamlCodec;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestYamlCodec
{
    @Test
    public void testYamlCodec()
    {
        YamlCodec<Person> codec = yamlCodec(Person.class);

        Person expected = new Person().setName("dain").setRocks(true);
        expected.setLastName(Optional.of("Awesome"));

        String yaml = codec.toYaml(expected);
        assertThat(yaml).contains("name");
        assertThat(yaml).contains("lastName");
        assertThat(codec.fromYaml(yaml)).isEqualTo(expected);

        byte[] bytes = codec.toYamlBytes(expected);
        assertThat(codec.fromYaml(bytes)).isEqualTo(expected);
        assertThat(codec.fromYaml(new ByteArrayInputStream(bytes))).isEqualTo(expected);
        assertThat(codec.fromYaml(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8))).isEqualTo(expected);
    }

    @Test
    public void testListYamlCodec()
    {
        YamlCodec<List<Person>> codec = listYamlCodec(Person.class);

        ImmutableList<Person> expected = ImmutableList.of(
                new Person().setName("dain").setRocks(true),
                new Person().setName("martin").setRocks(true));

        String yaml = codec.toYaml(expected);
        assertThat(codec.fromYaml(yaml)).isEqualTo(expected);
    }

    @Test
    public void testMapYamlCodec()
    {
        YamlCodec<Map<String, Person>> codec = mapYamlCodec(String.class, Person.class);

        ImmutableMap<String, Person> expected = ImmutableMap.<String, Person>builder()
                .put("dain", new Person().setName("dain").setRocks(true))
                .put("martin", new Person().setName("martin").setRocks(true))
                .build();

        String yaml = codec.toYaml(expected);
        assertThat(codec.fromYaml(yaml)).isEqualTo(expected);
    }

    @Test
    public void testTypeToken()
    {
        YamlCodec<List<Person>> codec = yamlCodec(new TypeToken<>() {});

        ImmutableList<Person> expected = ImmutableList.of(
                new Person().setName("dain").setRocks(true),
                new Person().setName("martin").setRocks(true));

        assertThat(codec.fromYaml(codec.toYaml(expected))).isEqualTo(expected);
    }

    @Test
    public void testMultiDocumentYamlIsRejected()
    {
        // FAIL_ON_TRAILING_TOKENS is inherited from the JSON base provider.
        // Multi-document YAML (a `---`-separated second document) counts as trailing tokens.
        String multiDoc =
                """
                name: dain
                rocks: true
                ---
                name: martin
                rocks: true
                """;

        YamlCodec<Person> codec = yamlCodec(Person.class);
        assertThatThrownBy(() -> codec.fromYaml(multiDoc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDuplicateKeyYamlIsRejected()
    {
        // SnakeYAML setAllowDuplicateKeys(false), duplicate keys are a correctness bug.
        String duplicate =
                """
                name: dain
                name: martin
                rocks: true
                """;

        YamlCodec<Person> codec = yamlCodec(Person.class);
        assertThatThrownBy(() -> codec.fromYaml(duplicate))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testLargeYamlInputIsAccepted()
    {
        // SnakeYAML's default codepoint limit is 3MB. Use ~4MB to prove that it's removed.
        String bigValue = "x".repeat(4 * 1024 * 1024);
        Person expected = new Person().setName(bigValue).setRocks(true);

        YamlCodec<Person> codec = yamlCodec(Person.class);
        String yaml = codec.toYaml(expected);
        assertThat(codec.fromYaml(yaml)).isEqualTo(expected);
    }

    @Test
    public void testPrettyPrintIsNoOp()
    {
        // YAML output is block-formatted regardless of SerializationFeature.INDENT_OUTPUT.
        // The prettyPrint() method is provided for API symmetry with JsonCodecFactory.
        YamlCodecFactory plain = new YamlCodecFactory();
        YamlCodecFactory pretty = plain.prettyPrint();

        Person expected = new Person().setName("dain").setRocks(true);
        String plainYaml = plain.yamlCodec(Person.class).toYaml(expected);
        String prettyYaml = pretty.yamlCodec(Person.class).toYaml(expected);

        assertThat(prettyYaml).isEqualTo(plainYaml);
    }

    @Test
    public void testGeneratorFeatureDefaults()
    {
        // Snapshot the human-readable defaults the provider sets so a future change is visible.
        Person expected = new Person().setName("dain").setRocks(true);

        String yaml = yamlCodec(Person.class).toYaml(expected);

        // WRITE_DOC_START_MARKER = false (no `---` prefix).
        assertThat(yaml).doesNotStartWith("---");
        // MINIMIZE_QUOTES = true ("dain" emits unquoted).
        assertThat(yaml).contains("name: dain");
        assertThat(yaml).doesNotContain("name: \"dain\"");
    }
}
