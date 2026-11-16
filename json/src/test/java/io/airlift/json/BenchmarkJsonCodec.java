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
package io.airlift.json;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Verify.verify;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class BenchmarkJsonCodec
{
    public record Item(String name, long id, double score, boolean active, List<String> tags, Map<String, String> attributes) {}

    public record Document(String id, String description, List<Item> items) {}

    public enum Payload
    {
        // a single object: 151 bytes, or 203 bytes indented
        SMALL(Item.class) {
            @Override
            Object create()
            {
                return item(1);
            }
        },
        // 100 nested objects: 15.8 KB, or 23.1 KB indented
        MEDIUM(Document.class) {
            @Override
            Object create()
            {
                ImmutableList.Builder<Item> items = ImmutableList.builder();
                for (int id = 0; id < 100; id++) {
                    items.add(item(id));
                }
                return new Document("document-1", "a document with one hundred items", items.build());
            }
        };

        private final Class<?> type;

        Payload(Class<?> type)
        {
            this.type = type;
        }

        abstract Object create();

        private static Item item(int id)
        {
            return new Item(
                    "item-" + id,
                    1_000_000_007L * id,
                    id / 8.0,
                    id % 2 == 0,
                    ImmutableList.of("red-" + id, "green-" + id, "blue-" + id),
                    ImmutableMap.of("owner", "owner-" + id, "region", "region-" + id));
        }
    }

    public enum Codec
    {
        // JsonCodec.jsonCodec(type)
        STATIC {
            @Override
            JsonCodec<?> create(Class<?> type)
            {
                return JsonCodec.jsonCodec(type);
            }
        },
        // new JsonCodecFactory().jsonCodec(type)
        FACTORY {
            @Override
            JsonCodec<?> create(Class<?> type)
            {
                return new JsonCodecFactory().jsonCodec(type);
            }
        },
        // new JsonCodecFactory().prettyPrint().jsonCodec(type)
        FACTORY_PRETTY {
            @Override
            JsonCodec<?> create(Class<?> type)
            {
                return new JsonCodecFactory().prettyPrint().jsonCodec(type);
            }
        };

        abstract JsonCodec<?> create(Class<?> type);
    }

    @Param({"SMALL", "MEDIUM"})
    private Payload payload = Payload.SMALL;

    @Param({"STATIC", "FACTORY", "FACTORY_PRETTY"})
    private Codec codec = Codec.STATIC;

    private JsonCodec<Object> jsonCodec;
    private Object value;
    private byte[] json;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup()
    {
        jsonCodec = (JsonCodec<Object>) codec.create(payload.type);
        value = payload.create();
        json = jsonCodec.toJsonBytes(value);
        verify(value.equals(jsonCodec.fromJson(json)), "value does not round trip");
    }

    @Benchmark
    public byte[] toJsonBytes()
    {
        return jsonCodec.toJsonBytes(value);
    }

    // decodes what the same codec wrote
    @Benchmark
    public Object fromJsonBytes()
    {
        return jsonCodec.fromJson(json);
    }

    public static void main(String[] args)
            throws RunnerException
    {
        Options options = new OptionsBuilder()
                .verbosity(VerboseMode.NORMAL)
                .include(".*\\." + BenchmarkJsonCodec.class.getSimpleName() + "\\..*")
                .addProfiler(GCProfiler.class)
                .build();

        new Runner(options).run();
    }
}
