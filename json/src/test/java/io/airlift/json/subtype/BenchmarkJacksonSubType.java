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
package io.airlift.json.subtype;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.jackson.JacksonSubType;
import io.airlift.json.JsonMapperProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Verify.verify;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class BenchmarkJacksonSubType
{
    private static final String DISCRIMINATOR = "type";

    public sealed interface Node
    {
        record Leaf(String name, long id, double weight, boolean enabled, List<String> tags)
                implements Node {}

        record Branch(String name, long id, Map<String, String> attributes, List<Node> children)
                implements Node {}
    }

    public enum Payload
    {
        // a single object, about 110 bytes
        SMALL {
            @Override
            Node create()
            {
                return leaf(1);
            }
        },
        // three levels of polymorphic objects, about 46 KB
        NESTED {
            @Override
            Node create()
            {
                ImmutableList.Builder<Node> branches = ImmutableList.builder();
                for (int branch = 0; branch < 16; branch++) {
                    ImmutableList.Builder<Node> leaves = ImmutableList.builder();
                    for (int leaf = 0; leaf < 22; leaf++) {
                        leaves.add(leaf(branch * 100 + leaf));
                    }
                    branches.add(branch(branch, leaves.build()));
                }
                return branch(1000, branches.build());
            }
        };

        abstract Node create();

        private static Node leaf(int id)
        {
            return new Node.Leaf("leaf-" + id, 1_000_000_007L * id, id / 8.0, id % 2 == 0, ImmutableList.of("red-" + id, "green-" + id, "blue-" + id));
        }

        private static Node branch(int id, List<Node> children)
        {
            return new Node.Branch("branch-" + id, 1_000_000_009L * id, ImmutableMap.of("owner", "owner-" + id, "region", "region-" + id), children);
        }
    }

    public enum DiscriminatorPosition
    {
        FIRST, LAST
    }

    @State(Scope.Thread)
    public static class EncodeData
    {
        @Param({"SMALL", "NESTED"})
        private Payload payload = Payload.SMALL;

        private Node value;
        private ObjectWriter writer;

        @Setup
        public void setup()
        {
            value = payload.create();
            writer = createJsonMapper().writerFor(Node.class);
        }
    }

    @State(Scope.Thread)
    public static class DecodeData
    {
        @Param({"SMALL", "NESTED"})
        private Payload payload = Payload.SMALL;

        @Param({"FIRST", "LAST"})
        private DiscriminatorPosition discriminator = DiscriminatorPosition.FIRST;

        private byte[] json;
        private ObjectReader reader;

        @Setup
        public void setup()
        {
            JsonMapper jsonMapper = createJsonMapper();
            reader = jsonMapper.readerFor(Node.class);

            Node value = payload.create();
            // independent of the position JacksonSubType writes the discriminator at
            json = jsonMapper.writeValueAsBytes(moveDiscriminator(jsonMapper, jsonMapper.valueToTree(value), discriminator));
            verify(value.equals(reader.readValue(json)), "value does not round trip");
        }
    }

    private static JsonMapper createJsonMapper()
    {
        JacksonSubType jacksonSubType = JacksonSubType.builder()
                .forBase(Node.class, DISCRIMINATOR)
                .addPermittedSubClasses()
                .build();
        return new JsonMapperProvider().withJacksonSubTypes(ImmutableSet.of(jacksonSubType)).get();
    }

    private static JsonNode moveDiscriminator(JsonMapper jsonMapper, JsonNode node, DiscriminatorPosition position)
    {
        if (node instanceof ArrayNode array) {
            ArrayNode result = jsonMapper.createArrayNode();
            array.forEach(element -> result.add(moveDiscriminator(jsonMapper, element, position)));
            return result;
        }
        if (node instanceof ObjectNode object) {
            ObjectNode result = jsonMapper.createObjectNode();
            JsonNode type = object.get(DISCRIMINATOR);
            if (type != null && position == DiscriminatorPosition.FIRST) {
                result.set(DISCRIMINATOR, type);
            }
            object.properties().stream()
                    .filter(entry -> !entry.getKey().equals(DISCRIMINATOR))
                    .forEach(entry -> result.set(entry.getKey(), moveDiscriminator(jsonMapper, entry.getValue(), position)));
            if (type != null && position == DiscriminatorPosition.LAST) {
                result.set(DISCRIMINATOR, type);
            }
            return result;
        }
        return node;
    }

    @Benchmark
    public Node decode(DecodeData data)
    {
        return data.reader.readValue(data.json);
    }

    @Benchmark
    public byte[] encode(EncodeData data)
    {
        return data.writer.writeValueAsBytes(data.value);
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkJacksonSubType.class.getSimpleName() + "\\..*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}
