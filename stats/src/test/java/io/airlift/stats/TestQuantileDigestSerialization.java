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
package io.airlift.stats;

import io.airlift.slice.BasicSliceInput;
import io.airlift.slice.DynamicSliceOutput;
import io.airlift.slice.Slice;
import io.airlift.slice.SliceInput;
import io.airlift.slice.SliceOutput;
import io.airlift.slice.Slices;
import io.airlift.testing.TestingTicker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Pins the serialized form of {@link QuantileDigest} and what its deserialization accepts to the
 * implementation that read and wrote every node through {@code SliceInput}/{@code SliceOutput},
 * which is kept here as {@link #referenceSerialize} and {@link #referenceDeserialize}.
 */
public class TestQuantileDigestSerialization
{
    private static final int HEADER_SIZE = 45;
    private static final int NODE_SIZE = 17;
    private static final int NODE_COUNT_OFFSET = HEADER_SIZE - Integer.BYTES;
    private static final int HAS_LEFT = 1;
    private static final int HAS_RIGHT = 2;
    // one node per level: 64 branch levels and the leaves
    private static final int MAX_PENDING_SUBTREES = 65;

    @Test
    public void testSerializedBytesMatchReference()
    {
        for (int seed = 0; seed < 2000; seed++) {
            QuantileDigest digest = randomDigest(new Random(seed));

            Slice serialized = digest.serialize();

            // serialize() compresses and then writes, so the digest is left in the state that was written
            assertThat(serialized)
                    .as("seed %s", seed)
                    .isEqualTo(referenceSerialize(stateOf(digest)));
            assertThat(serialized.byteArray()).hasSize(serialized.length());
            assertThat(serialized.length()).isEqualTo(digest.estimatedSerializedSizeInBytes());
        }
    }

    @Test
    public void testDeserializedDigestMatchesReference()
    {
        for (int seed = 0; seed < 2000; seed++) {
            Slice serialized = randomDigest(new Random(seed)).serialize();

            QuantileDigest deserialized = new QuantileDigest(serialized);
            deserialized.validate();
            assertSameState(stateOf(deserialized), referenceDeserialize(serialized), "seed " + seed);

            // a digest built by adding and merging never has more than one pending subtree per level
            assertThat(maxPendingSubtrees(serialized)).as("seed %s", seed).isLessThanOrEqualTo(MAX_PENDING_SUBTREES);
        }
    }

    @Test
    public void testPendingSubtreesOfDeepestDigest()
    {
        // Without compression, a value that differs from all the others at one bit gives a branch at
        // that bit's level. These sort before the others, so each one is a left leaf that waits on
        // the stack until the whole chain to its right is done.
        QuantileDigest digest = new QuantileDigest(0);
        digest.add(Long.MAX_VALUE);
        for (int bit = 0; bit < Long.SIZE; bit++) {
            digest.add(Long.MAX_VALUE ^ (1L << bit));
        }

        Slice serialized = digest.serialize();
        assertThat(maxPendingSubtrees(serialized)).isEqualTo(MAX_PENDING_SUBTREES);
        assertSameState(stateOf(new QuantileDigest(serialized)), referenceDeserialize(serialized), "deepest digest");
    }

    @Test
    public void testDeserializeTreeDeeperThanAnyDigest()
    {
        // 300 leaves followed by the 299 branches that join them from right to left. No digest has this
        // shape, since levels must decrease on the way down, but nothing in the format rules it out.
        int leaves = 300;
        int[] nodeStructures = new int[2 * leaves - 1];
        Arrays.fill(nodeStructures, leaves, nodeStructures.length, HAS_LEFT | HAS_RIGHT);
        Slice serialized = serializedTree(nodeStructures);
        assertThat(maxPendingSubtrees(serialized)).isEqualTo(leaves);

        assertSameState(stateOf(new QuantileDigest(serialized)), referenceDeserialize(serialized), "right-deep tree");
    }

    @Test
    public void testDeserializeRandomTree()
    {
        for (int seed = 0; seed < 20_000; seed++) {
            Random random = new Random(seed);

            // any shape and any levels, whether or not a digest could have them
            int[] nodeStructures = new int[random.nextInt(200)];
            int leafBias = 1 + random.nextInt(4);
            int pending = 0;
            for (int node = 0; node < nodeStructures.length; node++) {
                int children = random.nextInt(leafBias) == 0 ? Math.min(random.nextInt(4), 3) : 0;
                if (Integer.bitCount(children) > pending) {
                    children = 0;
                }
                nodeStructures[node] = (random.nextInt(64) << 2) | children;
                pending += 1 - Integer.bitCount(children);
            }
            // join what is left into a single tree
            int nodeCount = nodeStructures.length;
            nodeStructures = Arrays.copyOf(nodeStructures, nodeCount + Math.max(pending - 1, 0));
            Arrays.fill(nodeStructures, nodeCount, nodeStructures.length, HAS_LEFT | HAS_RIGHT);
            Slice serialized = serializedTree(nodeStructures);

            assertThat(catchThrowable(() -> referenceDeserialize(serialized))).as("seed %s", seed).isNull();
            assertSameOutcome(serialized, "seed " + seed);
        }
    }

    @Test
    public void testDeserializeRandomNodeStructures()
    {
        for (int seed = 0; seed < 20_000; seed++) {
            Random random = new Random(seed);

            // mostly rejected: a child that is not there, or more than one root
            int[] nodeStructures = new int[random.nextInt(50)];
            int leafBias = 1 + random.nextInt(4);
            for (int node = 0; node < nodeStructures.length; node++) {
                nodeStructures[node] = random.nextInt(leafBias) == 0 ? random.nextInt(256) : random.nextInt(64) << 2;
            }

            assertSameOutcome(serializedTree(nodeStructures), "seed " + seed);
        }
    }

    @Test
    public void testDeserializeCorrupted()
    {
        for (int seed = 0; seed < 20_000; seed++) {
            Random random = new Random(seed);
            byte[] bytes = smallRandomDigest(random).serialize().getBytes();

            switch (random.nextInt(4)) {
                case 0 -> {
                    // any byte but those of the node count
                    int index = random.nextInt(bytes.length - Integer.BYTES);
                    bytes[index < NODE_COUNT_OFFSET ? index : index + Integer.BYTES] = (byte) random.nextInt();
                }
                case 1 -> {
                    int nodeCount = (bytes.length - HEADER_SIZE) / NODE_SIZE;
                    if (nodeCount > 0) {
                        bytes[HEADER_SIZE + random.nextInt(nodeCount) * NODE_SIZE] ^= (byte) (1 << random.nextInt(Byte.SIZE));
                    }
                }
                case 2 -> {
                    // a node count that is off by a few, or negative
                    Slice slice = Slices.wrappedBuffer(bytes);
                    int delta = random.nextInt(4) == 0 ? -1000 : random.nextInt(7) - 3;
                    slice.setInt(NODE_COUNT_OFFSET, slice.getInt(NODE_COUNT_OFFSET) + delta);
                }
                default -> bytes = Arrays.copyOf(bytes, random.nextInt(bytes.length));
            }

            assertSameOutcome(Slices.wrappedBuffer(bytes), "seed " + seed);
        }
    }

    @Test
    public void testDeserializeTruncated()
    {
        Slice serialized = smallRandomDigest(new Random(1)).serialize();
        assertThat(serialized.length()).isGreaterThan(HEADER_SIZE + 10 * NODE_SIZE);

        for (int length = 0; length < serialized.length(); length++) {
            Slice truncated = serialized.slice(0, length);
            if (length > 0) {
                // the bytes that were cut off are still in the backing array, but must not be read
                assertThat(truncated.byteArray()).hasSize(serialized.length());
            }

            assertThatThrownBy(() -> new QuantileDigest(truncated))
                    .as("length %s", length)
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertSameOutcome(truncated, "length " + length);
        }
    }

    @Test
    public void testDeserializeNodeCountLargerThanInput()
    {
        byte[] bytes = smallRandomDigest(new Random(1)).serialize().getBytes();
        Slice serialized = Slices.wrappedBuffer(bytes);
        // min and max far enough apart for any node count to pass the height check
        serialized.setLong(HEADER_SIZE - Integer.BYTES - 2 * Long.BYTES, Long.MIN_VALUE);
        serialized.setLong(HEADER_SIZE - Integer.BYTES - Long.BYTES, Long.MAX_VALUE);

        // rejected before any array is allocated for the nodes
        for (int nodeCount : new int[] {(bytes.length - HEADER_SIZE) / NODE_SIZE + 1, 1 << 20, Integer.MAX_VALUE}) {
            serialized.setInt(NODE_COUNT_OFFSET, nodeCount);
            assertThatThrownBy(() -> new QuantileDigest(serialized))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }

        serialized.setInt(NODE_COUNT_OFFSET, -1);
        assertThatThrownBy(() -> new QuantileDigest(serialized))
                .isInstanceOf(NegativeArraySizeException.class);
    }

    @Test
    public void testDeserializeSliceView()
    {
        for (int seed = 0; seed < 200; seed++) {
            Random random = new Random(seed);
            Slice serialized = randomDigest(random).serialize();

            // somewhere inside a larger array, with other bytes on both sides
            byte[] bytes = new byte[serialized.length() + 2 * NODE_SIZE + random.nextInt(100)];
            random.nextBytes(bytes);
            int offset = 1 + random.nextInt(NODE_SIZE);
            serialized.getBytes(0, bytes, offset, serialized.length());

            Slice exact = Slices.wrappedBuffer(bytes, offset, serialized.length());
            assertSameState(stateOf(new QuantileDigest(exact)), referenceDeserialize(serialized), "seed " + seed);

            // bytes after the last node are ignored
            Slice withTrailingBytes = Slices.wrappedBuffer(bytes, offset, bytes.length - offset);
            assertSameState(stateOf(new QuantileDigest(withTrailingBytes)), referenceDeserialize(serialized), "seed " + seed);

            if (serialized.length() > HEADER_SIZE) {
                Slice truncated = Slices.wrappedBuffer(bytes, offset, serialized.length() - 1);
                assertThatThrownBy(() -> new QuantileDigest(truncated))
                        .as("seed %s", seed)
                        .isInstanceOf(IndexOutOfBoundsException.class);
            }
        }
    }

    private static QuantileDigest smallRandomDigest(Random random)
    {
        QuantileDigest digest = new QuantileDigest(0.1, random.nextBoolean() ? 0 : 0.01, new TestingTicker());
        int count = 20 + random.nextInt(40);
        for (int i = 0; i < count; i++) {
            digest.add(random.nextInt(1000), 1 + random.nextInt(3));
        }
        return digest;
    }

    private static QuantileDigest randomDigest(Random random)
    {
        double maxError = new double[] {0, 0.001, 0.01, 0.1, 1}[random.nextInt(5)];
        double alpha = random.nextInt(3) == 0 ? DecayConfig.computeAlpha(0.5, 60) : 0;
        TestingTicker ticker = new TestingTicker();
        int domain = random.nextInt(5);

        QuantileDigest result = new QuantileDigest(maxError, alpha, ticker);
        int parts = random.nextInt(4);
        for (int part = 0; part < parts; part++) {
            QuantileDigest digest = new QuantileDigest(maxError, alpha, ticker);
            int count = random.nextInt(random.nextBoolean() ? 20 : 3000);
            for (int i = 0; i < count; i++) {
                long value = switch (domain) {
                    case 0 -> random.nextInt(100);
                    case 1 -> random.nextLong();
                    case 2 -> (long) (random.nextGaussian() * 1_000_000_000);
                    case 3 -> (random.nextBoolean() ? 1 : -1) * (1L << random.nextInt(Long.SIZE));
                    default -> Long.MAX_VALUE - random.nextInt(1000);
                };
                double weight = switch (random.nextInt(8)) {
                    case 0 -> 0.001 + random.nextDouble() * 100;
                    case 1 -> 0x1p60 * random.nextDouble();
                    default -> 1;
                };
                digest.add(value, weight);
                if (alpha != 0 && random.nextInt(200) == 0) {
                    ticker.increment(random.nextInt(120), TimeUnit.SECONDS);
                }
            }
            if (random.nextBoolean()) {
                result.merge(digest);
            }
            else {
                digest.merge(result);
                result = digest;
            }
        }
        return result;
    }

    /**
     * A serialized digest with one node for each of the given bytes of level and child flags.
     */
    private static Slice serializedTree(int[] nodeStructures)
    {
        SliceOutput output = new DynamicSliceOutput(HEADER_SIZE + nodeStructures.length * NODE_SIZE);
        output.writeByte(0);
        output.writeDouble(0.01);
        output.writeDouble(0);
        output.writeLong(0);
        output.writeLong(Long.MIN_VALUE);
        output.writeLong(Long.MAX_VALUE);
        output.writeInt(nodeStructures.length);
        for (int node = 0; node < nodeStructures.length; node++) {
            output.writeByte(nodeStructures[node]);
            output.writeDouble(node + 0.5);
            output.writeLong(node * 0x9E3779B97F4A7C15L);
        }
        return output.slice();
    }

    private static int maxPendingSubtrees(Slice serialized)
    {
        int nodeCount = serialized.getInt(NODE_COUNT_OFFSET);
        int pending = 0;
        int max = 0;
        for (int node = 0; node < nodeCount; node++) {
            int nodeStructure = serialized.getByte(HEADER_SIZE + node * NODE_SIZE);
            pending -= Integer.bitCount(nodeStructure & (HAS_LEFT | HAS_RIGHT));
            pending++;
            max = Math.max(max, pending);
        }
        return max;
    }

    /**
     * Either both implementations reject the input, with the same kind of exception, or both accept
     * it and build the same digest.
     */
    private static void assertSameOutcome(Slice serialized, String description)
    {
        Throwable expectedFailure = catchThrowable(() -> referenceDeserialize(serialized));
        Throwable failure = catchThrowable(() -> new QuantileDigest(serialized));

        if (expectedFailure == null) {
            assertThat(failure).as(description).isNull();
            assertSameState(stateOf(new QuantileDigest(serialized)), referenceDeserialize(serialized), description);
        }
        else if (expectedFailure instanceof IndexOutOfBoundsException) {
            // the input is too short or a node has a child that is not there, whichever is noticed first
            assertThat(failure).as(description).isInstanceOf(IndexOutOfBoundsException.class);
        }
        else {
            assertThat(failure).as(description).isExactlyInstanceOf(expectedFailure.getClass());
        }
    }

    private static void assertSameState(DigestState actual, DigestState expected, String description)
    {
        assertThat(Double.doubleToRawLongBits(actual.maxError())).as(description).isEqualTo(Double.doubleToRawLongBits(expected.maxError()));
        assertThat(Double.doubleToRawLongBits(actual.alpha())).as(description).isEqualTo(Double.doubleToRawLongBits(expected.alpha()));
        assertThat(actual.landmarkInSeconds()).as(description).isEqualTo(expected.landmarkInSeconds());
        assertThat(actual.min()).as(description).isEqualTo(expected.min());
        assertThat(actual.max()).as(description).isEqualTo(expected.max());
        assertThat(Double.doubleToRawLongBits(actual.weightedCount())).as(description).isEqualTo(Double.doubleToRawLongBits(expected.weightedCount()));
        assertThat(actual.root()).as(description).isEqualTo(expected.root());
        assertThat(actual.nodeCount()).as(description).isEqualTo(expected.nodeCount());
        assertThat(Arrays.stream(actual.counts()).mapToLong(Double::doubleToRawLongBits).toArray())
                .as(description)
                .isEqualTo(Arrays.stream(expected.counts()).mapToLong(Double::doubleToRawLongBits).toArray());
        assertThat(actual.levels()).as(description).isEqualTo(expected.levels());
        assertThat(actual.values()).as(description).isEqualTo(expected.values());
        assertThat(actual.lefts()).as(description).isEqualTo(expected.lefts());
        assertThat(actual.rights()).as(description).isEqualTo(expected.rights());
    }

    private record DigestState(
            double maxError,
            double alpha,
            long landmarkInSeconds,
            long min,
            long max,
            double weightedCount,
            int root,
            int nodeCount,
            double[] counts,
            byte[] levels,
            long[] values,
            int[] lefts,
            int[] rights) {}

    private static DigestState stateOf(QuantileDigest digest)
    {
        DecayState decay = (DecayState) field(digest, "decay");
        return new DigestState(
                digest.getMaxError(),
                digest.getAlpha(),
                decay == null ? 0 : decay.getLandmarkInSeconds(),
                (long) field(digest, "min"),
                (long) field(digest, "max"),
                (double) field(digest, "weightedCount"),
                (int) field(digest, "root"),
                digest.getNodeCount(),
                (double[]) field(digest, "counts"),
                (byte[]) field(digest, "levels"),
                (long[]) field(digest, "values"),
                (int[]) field(digest, "lefts"),
                (int[]) field(digest, "rights"));
    }

    private static Object field(QuantileDigest digest, String name)
    {
        try {
            Field field = QuantileDigest.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(digest);
        }
        catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * What {@code serialize()} did after compressing, before it wrote the nodes into an array.
     */
    private static Slice referenceSerialize(DigestState state)
    {
        SliceOutput output = new DynamicSliceOutput(HEADER_SIZE + state.nodeCount() * NODE_SIZE);

        output.writeByte(0);
        output.writeDouble(state.maxError());
        output.writeDouble(state.alpha());
        output.writeLong(state.landmarkInSeconds());
        output.writeLong(state.min());
        output.writeLong(state.max());
        output.writeInt(state.nodeCount());

        int[] nodes = new int[state.nodeCount()];
        int nodeCount = referencePostOrder(state, state.root(), nodes, 0);
        assertThat(nodeCount).isEqualTo(state.nodeCount());

        for (int node : nodes) {
            byte nodeStructure = (byte) (Math.max(state.levels()[node] - 1, 0) << 2);
            if (state.lefts()[node] != -1) {
                nodeStructure |= HAS_LEFT;
            }
            if (state.rights()[node] != -1) {
                nodeStructure |= HAS_RIGHT;
            }
            output.writeByte(nodeStructure);
            output.writeDouble(state.counts()[node]);
            output.writeLong(state.values()[node]);
        }

        return output.slice();
    }

    private static int referencePostOrder(DigestState state, int node, int[] nodes, int index)
    {
        if (node == -1) {
            return index;
        }
        index = referencePostOrder(state, state.lefts()[node], nodes, index);
        index = referencePostOrder(state, state.rights()[node], nodes, index);
        nodes[index] = node;
        return index + 1;
    }

    /**
     * The deserializing constructor as it was when it read every node through a {@code SliceInput}.
     */
    private static DigestState referenceDeserialize(Slice serialized)
    {
        SliceInput input = new BasicSliceInput(serialized);

        byte format = input.readByte();
        checkArgument(format == 0, "Invalid format");
        double maxError = input.readDouble();
        double alpha = input.readDouble();
        long landmarkInSeconds = input.readLong();
        if (alpha != 0.0) {
            DecayConfig.of(alpha).newState(landmarkInSeconds);
        }

        long min = input.readLong();
        long max = input.readLong();
        int nodeCount = input.readInt();

        int height = 64 - Long.numberOfLeadingZeros(min ^ max) + 1;
        checkArgument(height >= 64 || nodeCount <= (1L << height) - 1, "Too many nodes in deserialized tree. Possible corruption");

        double[] counts = new double[nodeCount];
        byte[] levels = new byte[nodeCount];
        long[] values = new long[nodeCount];

        int[] stack = new int[(Integer.highestOneBit(nodeCount - 1) << 1) + 1]; // log2 ceiling
        int top = -1;

        double weightedCount = 0;
        int[] lefts = new int[nodeCount];
        int[] rights = new int[nodeCount];
        for (int node = 0; node < nodeCount; node++) {
            byte nodeStructure = input.readByte();
            boolean hasRight = (nodeStructure & HAS_RIGHT) != 0;
            boolean hasLeft = (nodeStructure & HAS_LEFT) != 0;
            byte level = (byte) ((nodeStructure >>> 2) & 0b111111);

            if (hasLeft || hasRight) {
                level++;
            }
            levels[node] = level;

            if (hasRight) {
                rights[node] = stack[top--];
            }
            else {
                rights[node] = -1;
            }

            if (hasLeft) {
                lefts[node] = stack[top--];
            }
            else {
                lefts[node] = -1;
            }

            stack[++top] = node;

            double count = input.readDouble();
            weightedCount += count;
            counts[node] = count;
            values[node] = input.readLong();
        }
        checkArgument(nodeCount == 0 || top == 0, "Tree is corrupted. Expected a single root node");

        // the landmark is kept in the decay state, which a digest that does not decay does not have
        return new DigestState(maxError, alpha, alpha == 0.0 ? 0 : landmarkInSeconds, min, max, weightedCount, nodeCount - 1, nodeCount, counts, levels, values, lefts, rights);
    }
}
