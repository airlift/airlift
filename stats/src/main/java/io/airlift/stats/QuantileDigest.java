package io.airlift.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.PeekingIterator;
import com.google.common.util.concurrent.AtomicDouble;
import org.openjdk.jol.info.ClassLayout;

import javax.annotation.concurrent.NotThreadSafe;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

/**
 * <p>Implements http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.132.7343, a data structure
 * for approximating quantiles by trading off error with memory requirements.</p>
 *
 * <p>The size of the digest is adjusted dynamically to achieve the error bound and requires
 * O(log2(U) / maxError) space, where <em>U</em> is the number of bits needed to represent the
 * domain of the values added to the digest. The error is defined as the discrepancy between the
 * real rank of the value returned in a quantile query and the rank corresponding to the queried
 * quantile.</p>
 *
 * <p>Thus, for a query for quantile <em>q</em> that returns value <em>v</em>, the error is
 * |rank(v) - q * N| / N, where N is the number of elements added to the digest and rank(v) is the
 * real rank of <em>v</em></p>
 *
 * <p>This class also supports exponential decay. The implementation is based on the ideas laid out
 * in http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.159.3978</p>
 */
@NotThreadSafe
public class QuantileDigest
{
    private static final int MAX_BITS = 64;
    private static final double MAX_SIZE_FACTOR = 1.5;

    // needs to be such that Math.exp(alpha * seconds) does not grow too big
    static final long RESCALE_THRESHOLD_SECONDS = 50;
    static final double ZERO_WEIGHT_THRESHOLD = 1e-5;

    private final double maxError;
    private final Ticker ticker;
    private final double alpha;
    private final boolean compressAutomatically;

    private Node root;

    private double weightedCount;
    private long max = Long.MIN_VALUE;
    private long min = Long.MAX_VALUE;

    private long landmarkInSeconds;

    private int totalNodeCount = 0;
    private int nonZeroNodeCount = 0;
    private int compressions = 0;

    private enum TraversalOrder
    {
        FORWARD, REVERSE
    }

    /**
     * <p>Create a QuantileDigest with a maximum error guarantee of "maxError" and no decay.
     *
     * @param maxError the max error tolerance
     */
    public QuantileDigest(double maxError)
    {
        this(maxError, 0);
    }

    /**
     *<p>Create a QuantileDigest with a maximum error guarantee of "maxError" and exponential decay
     * with factor "alpha".</p>
     *
     * @param maxError the max error tolerance
     * @param alpha the exponential decay factor
     */
    public QuantileDigest(double maxError, double alpha)
    {
        this(maxError, alpha, Ticker.systemTicker(), true);
    }

    @VisibleForTesting
    QuantileDigest(double maxError, double alpha, Ticker ticker, boolean compressAutomatically)
    {
        checkArgument(maxError >= 0 && maxError <= 1, "maxError must be in range [0, 1]");
        checkArgument(alpha >= 0 && alpha < 1, "alpha must be in range [0, 1)");

        this.maxError = maxError;
        this.alpha = alpha;
        this.ticker = ticker;
        this.compressAutomatically = compressAutomatically;

        landmarkInSeconds = TimeUnit.NANOSECONDS.toSeconds(ticker.read());
    }

    public QuantileDigest(QuantileDigest quantileDigest)
    {
        this(quantileDigest.getMaxError(), quantileDigest.getAlpha());
        merge(quantileDigest);
    }

    public double getMaxError()
    {
        return maxError;
    }

    public double getAlpha()
    {
        return alpha;
    }

    public void add(long value)
    {
        add(value, 1);
    }

    /**
     * Adds a value to this digest. The value must be {@code >= 0}
     */
    public void add(long value, long count)
    {
        checkArgument(count > 0, "count must be > 0");

        long nowInSeconds = TimeUnit.NANOSECONDS.toSeconds(ticker.read());

        int maxExpectedNodeCount = 3 * calculateCompressionFactor();
        if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
            rescale(nowInSeconds);
            compress(); // need to compress to get rid of nodes that may have decayed to ~ 0
        }
        else if (nonZeroNodeCount > MAX_SIZE_FACTOR * maxExpectedNodeCount && compressAutomatically) {
            // The size (number of non-zero nodes) of the digest is at most 3 * compression factor
            // If we're over MAX_SIZE_FACTOR of the expected size, compress
            // Note: we don't compress as soon as we go over expectedNodeCount to avoid unnecessarily
            // running a compression for every new added element when we're close to boundary
            compress();
        }

        double weight = weight(TimeUnit.NANOSECONDS.toSeconds(ticker.read())) * count;

        max = Math.max(max, value);
        min = Math.min(min, value);

        insert(longToBits(value), weight);
    }

    public void merge(QuantileDigest other)
    {
        rescaleToCommonLandmark(this, other);

        // 2. merge other into this (don't modify other)
        root = merge(root, other.root);

        max = Math.max(max, other.max);
        min = Math.min(min, other.min);

        // 3. compress to remove unnecessary nodes
        compress();
    }

    /**
     * Get a lower bound on the quantiles for the given proportions. A returned q quantile is guaranteed to be within
     * the q - maxError and q quantiles.
     * <p>
     * The input list of quantile proportions must be sorted in increasing order, and each value must be in the range [0, 1]
     */
    public List<Long> getQuantilesLowerBound(List<Double> quantiles)
    {
        checkArgument(Ordering.natural().isOrdered(quantiles), "quantiles must be sorted in increasing order");
        for (double quantile : quantiles) {
            checkArgument(quantile >= 0 && quantile <= 1, "quantile must be between [0,1]");
        }

        List<Double> reversedQuantiles = ImmutableList.copyOf(quantiles).reverse();

        ImmutableList.Builder<Long> builder = ImmutableList.builder();
        PeekingIterator<Double> iterator = Iterators.peekingIterator(reversedQuantiles.iterator());

        postOrderTraversal(root, new Callback()
        {
            private double sum;

            @Override
            public boolean process(Node node)
            {
                sum += node.weightedCount;

                while (iterator.hasNext() && sum > (1.0 - iterator.peek()) * weightedCount) {
                    iterator.next();

                    // we know the min value ever seen, so cap the percentile to provide better error
                    // bounds in this case
                    long value = Math.max(node.getLowerBound(), min);

                    builder.add(value);
                }

                return iterator.hasNext();
            }
        }, TraversalOrder.REVERSE);

        // we finished the traversal without consuming all quantiles. This means the remaining quantiles
        // correspond to the max known value
        while (iterator.hasNext()) {
            builder.add(min);
            iterator.next();
        }

        return builder.build().reverse();
    }

    /**
     * Get an upper bound on the quantiles for the given proportions. A returned q quantile is guaranteed to be within
     * the q and q + maxError quantiles.
     * <p>
     * The input list of quantile proportions must be sorted in increasing order, and each value must be in the range [0, 1]
     */
    public List<Long> getQuantilesUpperBound(List<Double> quantiles)
    {
        checkArgument(Ordering.natural().isOrdered(quantiles), "quantiles must be sorted in increasing order");
        for (double quantile : quantiles) {
            checkArgument(quantile >= 0 && quantile <= 1, "quantile must be between [0,1]");
        }

        final ImmutableList.Builder<Long> builder = ImmutableList.builder();
        final PeekingIterator<Double> iterator = Iterators.peekingIterator(quantiles.iterator());

        postOrderTraversal(root, new Callback()
        {
            private double sum = 0;

            public boolean process(Node node)
            {
                sum += node.weightedCount;

                while (iterator.hasNext() && sum > iterator.peek() * weightedCount) {
                    iterator.next();

                    // we know the max value ever seen, so cap the percentile to provide better error
                    // bounds in this case
                    long value = Math.min(node.getUpperBound(), max);

                    builder.add(value);
                }

                return iterator.hasNext();
            }
        });

        // we finished the traversal without consuming all quantiles. This means the remaining quantiles
        // correspond to the max known value
        while (iterator.hasNext()) {
            builder.add(max);
            iterator.next();
        }

        return builder.build();
    }

    public List<Long> getQuantiles(List<Double> quantiles)
    {
        List<Long> accumulator = new ArrayList<Long>(quantiles.size());

        for (double q : quantiles) {
            accumulator.add(getQuantile(q));
        }
        return accumulator;
    }

    /**
     * Gets the value at the specified quantile +/- maxError. The quantile must be in the range [0, 1]
     */
    public long getQuantile(double quantile)
    {
        return getQuantileFromCDF(quantile);
    }

    public long getQuantileLowerBound(double quantile)
    {
        return getQuantilesLowerBound(ImmutableList.of(quantile)).get(0);
    }

    public long getQuantileUpperBound(double quantile)
    {
        return getQuantilesUpperBound(ImmutableList.of(quantile)).get(0);
    }

    /**
     * Number (decayed) of elements added to this quantile digest
     */
    public double getCount()
    {
        return weightedCount / weight(TimeUnit.NANOSECONDS.toSeconds(ticker.read()));
    }

    /*
    * Get the exponentially-decayed approximate counts of values in multiple buckets. The elements in
    * the provided list denote the upper bound each of the buckets and must be sorted in ascending
    * order.
    *
    * The approximate count in each bucket is guaranteed to be within 2 * totalCount * maxError of
    * the real count.
    */
    public List<Bucket> getHistogram(List<Long> bucketUpperBounds)
    {
        checkArgument(Ordering.natural().isOrdered(bucketUpperBounds), "buckets must be sorted in increasing order");

        final ImmutableList.Builder<Bucket> builder = ImmutableList.builder();
        final PeekingIterator<Long> iterator = Iterators.peekingIterator(bucketUpperBounds.iterator());

        final AtomicDouble sum = new AtomicDouble();
        final AtomicDouble lastSum = new AtomicDouble();

        // for computing weighed average of values in bucket
        final AtomicDouble bucketWeightedSum = new AtomicDouble();

        final double normalizationFactor = weight(TimeUnit.NANOSECONDS.toSeconds(ticker.read()));

        postOrderTraversal(root, new Callback()
        {
            public boolean process(Node node)
            {

                while (iterator.hasNext() && iterator.peek() <= node.getUpperBound()) {
                    double bucketCount = sum.get() - lastSum.get();

                    Bucket bucket = new Bucket(bucketCount / normalizationFactor, bucketWeightedSum.get() / bucketCount);

                    builder.add(bucket);
                    lastSum.set(sum.get());
                    bucketWeightedSum.set(0);
                    iterator.next();
                }

                bucketWeightedSum.addAndGet(node.getMiddle() * node.weightedCount);
                sum.addAndGet(node.weightedCount);
                return iterator.hasNext();
            }
        });

        while (iterator.hasNext()) {
            double bucketCount = sum.get() - lastSum.get();
            Bucket bucket = new Bucket(bucketCount / normalizationFactor, bucketWeightedSum.get() / bucketCount);

            builder.add(bucket);

            iterator.next();
        }

        return builder.build();
    }

    /*
     * Let F be the CDF (Cumulative Distribution Function) of the data
     * The q quantile is defined to be F^-1(q)
     *
     * The Q-digest stores a discretized copy of the data that is efficienctly stored with counts
     * This means only an approximation Fhat to F can be computed
     *
     * The naive Q-Digest algorithm uses a worst case approximation Fhat that is guaranteed to be <= F
     * This is very poor in practice in areas where the density is low. It works by assuming that any
     * discretized point has value equal to the largest possible value it can take.
     *
     * This implements an approximation Fhat that performs linear interpolation over the range of possible values.
     * Equivalanetly, a discretized point is treated as encoding a uniform distribution over the range of possible
     * values it could have been discretized from.
     */
    private double getCDF(long x)
    {
        double[] totalWeight = {0.0};

        inOrderTraversal(root, new Callback() {
            protected double sum = 0.0;

            public boolean process(Node node)
            {
                long lower = Math.max(min, node.getLowerBound());
                long upper = Math.min(max, node.getUpperBound());

                if (x >= upper) {
                    sum += node.weightedCount;
                    if (node.isLeaf() && x == upper) {
                        totalWeight[0] = sum;
                        return false;
                    }
                    totalWeight[0] = sum;
                    return true;
                } else if (x < lower) {
                    // Stop if gone too far
                    totalWeight[0] = sum;
                    return false;
                } else {
                    // interpolate by assuming a uniform distribution
                    // (lower, upper) represent the range of possible values
                    // In the case where the node overlaps with the max (or min) and all the mass in
                    // the children is only on the side closer to the median,
                    // assume the distribution is skewed and put all the mass on that side except for
                    // mass 1 on the min/max itself
                    double ratio = (x - lower) / (double) (upper - lower);
                    if (max <= upper && node.right == null && node.left != null) {
                        long middle = Math.min(max, node.getMiddle());
                        ratio = Math.min(middle - lower, x - lower) / (double) Math.max(1, middle - lower);
                    } else if (max >= lower && node.left == null && node.right != null) {
                        long middle = Math.max(min, node.getMiddle());
                        ratio = Math.max(0, x - middle) / (double) Math.max(1, upper - middle);
                    }
                    sum += node.weightedCount * ratio;

                }

                totalWeight[0] = sum;
                return true;
            }
        }, TraversalOrder.FORWARD);

        return totalWeight[0] / weightedCount;
    }

    public long getQuantileFromCDF(double p)
    {
        if (root == null) {
            return Long.MAX_VALUE;
        }

        final double tol = 1e-9;
        long lower = root.getLowerBound();
        long upper = root.getUpperBound();
        long middle = lower;
        double q = getCDF(lower);

        if (q > p) {
            return min;
        }

        while( (upper - lower) > 1 ){
            middle = lower + (upper - lower) / 2;
            q = getCDF(middle);

            if (q > p) {
                upper = middle;
            } else {
                lower = middle;
            }
        }

        q = getCDF(lower);

        // Preserve unusual quantile semantics where the cdf
        // F(x) = P(X <= x + 1/n) rather than P(X <= x)
        if (q < p + 1.0 / weightedCount + tol) {
            upper = Math.min(max, upper);
            return upper;
        } else {
            lower = Math.max(min, lower);
            return lower;
        }

    }

    public long getMin()
    {
        final AtomicLong chosen = new AtomicLong(min);
        postOrderTraversal(root, new Callback()
        {
            public boolean process(Node node)
            {
                if (node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
                    chosen.set(node.getLowerBound());
                    return false;
                }
                return true;
            }
        }, TraversalOrder.FORWARD);

        return Math.max(min, chosen.get());
    }

    public long getMax()
    {
        final AtomicLong chosen = new AtomicLong(max);
        postOrderTraversal(root, new Callback()
        {
            public boolean process(Node node)
            {
                if (node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
                    chosen.set(node.getUpperBound());
                    return false;
                }
                return true;
            }
        }, TraversalOrder.REVERSE);

        return Math.min(max, chosen.get());
    }

    public int estimatedInMemorySizeInBytes()
    {
        return SizeOf.QUANTILE_DIGEST + totalNodeCount * SizeOf.NODE;
    }

    public int estimatedSerializedSizeInBytes()
    {
        int estimatedNodeSize = SizeOf.BYTE + // flags
                SizeOf.BYTE + // level
                SizeOf.LONG + // value
                SizeOf.DOUBLE; // weight

        return SizeOf.DOUBLE + // maxError
                SizeOf.DOUBLE + // alpha
                SizeOf.LONG + // landmark
                SizeOf.LONG + // min
                SizeOf.LONG + // max
                SizeOf.INTEGER + // node count
                totalNodeCount * estimatedNodeSize;
    }

    public void serialize(final DataOutput output)
    {
        try {
            output.writeDouble(maxError);
            output.writeDouble(alpha);
            output.writeLong(landmarkInSeconds);
            output.writeLong(min);
            output.writeLong(max);
            output.writeInt(totalNodeCount);

            postOrderTraversal(root, new Callback()
            {
                @Override
                public boolean process(Node node)
                {
                    try {
                        serializeNode(output, node);
                    }
                    catch (IOException e) {
                        Throwables.propagate(e);
                    }
                    return true;
                }
            });
        }
        catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private void serializeNode(DataOutput output, Node node)
            throws IOException
    {
        int flags = 0;
        if (node.left != null) {
            flags |= Flags.HAS_LEFT;
        }
        if (node.right != null) {
            flags |= Flags.HAS_RIGHT;
        }

        output.writeByte(flags);
        output.writeByte(node.level);
        output.writeLong(node.bits);
        output.writeDouble(node.weightedCount);
    }

    public static QuantileDigest deserialize(DataInput input)
    {
        try {
            double maxError = input.readDouble();
            double alpha = input.readDouble();

            QuantileDigest result = new QuantileDigest(maxError, alpha);

            result.landmarkInSeconds = input.readLong();
            result.min = input.readLong();
            result.max = input.readLong();
            result.totalNodeCount = input.readInt();

            Deque<Node> stack = new ArrayDeque<>();
            for (int i = 0; i < result.totalNodeCount; i++) {
                int flags = input.readByte();

                Node node = deserializeNode(input);

                if ((flags & Flags.HAS_RIGHT) != 0) {
                    node.right = stack.pop();
                }

                if ((flags & Flags.HAS_LEFT) != 0) {
                    node.left = stack.pop();
                }

                stack.push(node);
                result.weightedCount += node.weightedCount;
                if (node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
                    result.nonZeroNodeCount++;
                }
            }


            if (!stack.isEmpty()) {
                Preconditions.checkArgument(stack.size() == 1, "Tree is corrupted. Expected a single root node");
                result.root = stack.pop();
            }

            return result;
        }
        catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static Node deserializeNode(DataInput input)
            throws IOException
    {
        int level = input.readUnsignedByte();
        long value = input.readLong();
        double weight = input.readDouble();

        return new Node(value, level, weight);
    }

    @VisibleForTesting
    int getTotalNodeCount()
    {
        return totalNodeCount;
    }

    @VisibleForTesting
    int getNonZeroNodeCount()
    {
        return nonZeroNodeCount;
    }

    @VisibleForTesting
    int getCompressions()
    {
        return compressions;
    }

    @VisibleForTesting
    void compress()
    {
        ++compressions;

        final int compressionFactor = calculateCompressionFactor();

        postOrderTraversal(root, new Callback()
        {
            public boolean process(Node node)
            {
                if (node.isLeaf()) {
                    return true;
                }

                // if children's weights are ~0 remove them and shift the weight to their parent

                double leftWeight = 0;
                if (node.left != null) {
                    leftWeight = node.left.weightedCount;
                }

                double rightWeight = 0;
                if (node.right != null) {
                    rightWeight = node.right.weightedCount;
                }

                boolean shouldCompress = node.weightedCount + leftWeight + rightWeight < (int) (weightedCount / compressionFactor);

                double oldNodeWeight = node.weightedCount;
                if (shouldCompress || leftWeight < ZERO_WEIGHT_THRESHOLD) {
                    node.left = tryRemove(node.left);

                    weightedCount += leftWeight;
                    node.weightedCount += leftWeight;
                }

                if (shouldCompress || rightWeight < ZERO_WEIGHT_THRESHOLD) {
                    node.right = tryRemove(node.right);

                    weightedCount += rightWeight;
                    node.weightedCount += rightWeight;
                }

                if (oldNodeWeight < ZERO_WEIGHT_THRESHOLD && node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
                    ++nonZeroNodeCount;
                }

                return true;
            }
        });

        if (root != null && root.weightedCount < ZERO_WEIGHT_THRESHOLD) {
            root = tryRemove(root);
        }
    }

    private double weight(long timestamp)
    {
        return Math.exp(alpha * (timestamp - landmarkInSeconds));
    }

    private void rescale(long newLandmarkInSeconds)
    {
        // rescale the weights based on a new landmark to avoid numerical overflow issues

        final double factor = Math.exp(-alpha * (newLandmarkInSeconds - landmarkInSeconds));

        weightedCount *= factor;

        postOrderTraversal(root, new Callback()
        {
            public boolean process(Node node)
            {
                double oldWeight = node.weightedCount;

                node.weightedCount *= factor;

                if (oldWeight >= ZERO_WEIGHT_THRESHOLD && node.weightedCount < ZERO_WEIGHT_THRESHOLD) {
                    --nonZeroNodeCount;
                }

                return true;
            }
        });

        landmarkInSeconds = newLandmarkInSeconds;
    }

    private int calculateCompressionFactor()
    {
        if (root == null) {
            return 1;
        }

        return Math.max((int) ((root.level + 1) / maxError), 1);
    }

    private void insert(long bits, double weight)
    {
        long lastBranch = 0;
        Node parent = null;
        Node current = root;

        while (true) {
            if (current == null) {
                setChild(parent, lastBranch, createLeaf(bits, weight));
                return;
            }
            else if (!inSameSubtree(bits, current.bits, current.level)) {
                // if bits and node.bits are not in the same branch given node's level,
                // insert a parent above them at the point at which branches diverge
                setChild(parent, lastBranch, makeSiblings(current, createLeaf(bits, weight)));
                return;
            }
            else if (current.level == 0 && current.bits == bits) {
                // found the node

                double oldWeight = current.weightedCount;

                current.weightedCount += weight;

                if (current.weightedCount >= ZERO_WEIGHT_THRESHOLD && oldWeight < ZERO_WEIGHT_THRESHOLD) {
                    ++nonZeroNodeCount;
                }

                weightedCount += weight;

                return;
            }

            // we're on the correct branch of the tree and we haven't reached a leaf, so keep going down
            long branch = bits & current.getBranchMask();

            parent = current;
            lastBranch = branch;

            if (branch == 0) {
                current = current.left;
            }
            else {
                current = current.right;
            }
        }
    }

    private void setChild(Node parent, long branch, Node child)
    {
        if (parent == null) {
            root = child;
        }
        else if (branch == 0) {
            parent.left = child;
        }
        else {
            parent.right = child;
        }
    }

    private Node makeSiblings(Node node, Node sibling)
    {
        int parentLevel = MAX_BITS - Long.numberOfLeadingZeros(node.bits ^ sibling.bits);

        Node parent = createNode(node.bits, parentLevel, 0);

        // the branch is given by the bit at the level one below parent
        long branch = sibling.bits & parent.getBranchMask();
        if (branch == 0) {
            parent.left = sibling;
            parent.right = node;
        }
        else {
            parent.left = node;
            parent.right = sibling;
        }

        return parent;
    }

    private Node createLeaf(long bits, double weight)
    {
        return createNode(bits, 0, weight);
    }

    private Node createNode(long bits, int level, double weight)
    {
        weightedCount += weight;
        ++totalNodeCount;
        if (weight >= ZERO_WEIGHT_THRESHOLD) {
            nonZeroNodeCount++;
        }
        return new Node(bits, level, weight);
    }

    private Node merge(Node node, Node other)
    {
        if (node == null) {
            return copyRecursive(other);
        }
        else if (other == null) {
            return node;
        }
        else if (!inSameSubtree(node.bits, other.bits, Math.max(node.level, other.level))) {
            return makeSiblings(node, copyRecursive(other));
        }
        else if (node.level > other.level) {
            long branch = other.bits & node.getBranchMask();

            if (branch == 0) {
                node.left = merge(node.left, other);
            }
            else {
                node.right = merge(node.right, other);
            }
            return node;
        }
        else if (node.level < other.level) {
            Node result = createNode(other.bits, other.level, other.weightedCount);

            long branch = node.bits & other.getBranchMask();
            if (branch == 0) {
                result.left = merge(node, other.left);
                result.right = copyRecursive(other.right);
            }
            else {
                result.left = copyRecursive(other.left);
                result.right = merge(node, other.right);
            }

            return result;
        }

        // else, they must be at the same level and on the same path, so just bump the counts
        double oldWeight = node.weightedCount;

        weightedCount += other.weightedCount;
        node.weightedCount = node.weightedCount + other.weightedCount;
        node.left = merge(node.left, other.left);
        node.right = merge(node.right, other.right);

        if (oldWeight < ZERO_WEIGHT_THRESHOLD && node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
            nonZeroNodeCount++;
        }

        return node;
    }

    private static boolean inSameSubtree(long bitsA, long bitsB, int level)
    {
        return level == MAX_BITS || (bitsA >>> level) == (bitsB >>> level);
    }

    private Node copyRecursive(Node node)
    {
        Node result = null;

        if (node != null) {
            result = createNode(node.bits, node.level, node.weightedCount);
            result.left = copyRecursive(node.left);
            result.right = copyRecursive(node.right);
        }

        return result;
    }

    /**
     * Remove the node if possible or set its count to 0 if it has children and
     * it needs to be kept around
     */
    private Node tryRemove(Node node)
    {
        if (node == null) {
            return null;
        }

        if (node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
            --nonZeroNodeCount;
        }

        weightedCount -= node.weightedCount;

        Node result = null;
        if (node.isLeaf()) {
            --totalNodeCount;
        }
        else if (node.hasSingleChild()) {
            result = node.getSingleChild();
            --totalNodeCount;
        }
        else {
            node.weightedCount = 0;
            result = node;
        }

        return result;
    }

    private boolean postOrderTraversal(Node node, Callback callback)
    {
        return postOrderTraversal(node, callback, TraversalOrder.FORWARD);
    }

    // returns true if traversal should continue
    private boolean postOrderTraversal(Node node, Callback callback, TraversalOrder order)
    {
        if (node == null) {
            return false;
        }

        Node first;
        Node second;

        if (order == TraversalOrder.FORWARD) {
            first = node.left;
            second = node.right;
        }
        else {
            first = node.right;
            second = node.left;
        }

        if (first != null && !postOrderTraversal(first, callback, order)) {
            return false;
        }

        if (second != null && !postOrderTraversal(second, callback, order)) {
            return false;
        }

        return callback.process(node);
    }

    private boolean inOrderTraversal(Node node, Callback callback, TraversalOrder order)
    {
        if (node == null) {
            return false;
        }

        Node first;
        Node second;

        if (order == TraversalOrder.FORWARD) {
            first = node.left;
            second = node.right;
        }
        else {
            first = node.right;
            second = node.left;
        }

        if (first != null && !inOrderTraversal(first, callback, order)) {
            return false;
        }

        if(!callback.process(node)) {
            return false;
        }

        if (second != null && !inOrderTraversal(second, callback, order)) {
            return false;
        }

        return true;
    }

    /**
     * Computes the maximum error of the current digest
     */
    public double getConfidenceFactor()
    {
        return computeMaxPathWeight(root) * 1.0 / weightedCount;
    }

    public boolean equivalent(QuantileDigest other)
    {
        rescaleToCommonLandmark(this, other);

        return (totalNodeCount == other.totalNodeCount &&
                nonZeroNodeCount == other.nonZeroNodeCount &&
                min == other.min &&
                max == other.max &&
                weightedCount == other.weightedCount &&
                Objects.equal(root, other.root));
    }

    private void rescaleToCommonLandmark(QuantileDigest one, QuantileDigest two)
    {
        long nowInSeconds = TimeUnit.NANOSECONDS.toSeconds(ticker.read());

        // 1. rescale this and other to common landmark
        long targetLandmark = Math.max(one.landmarkInSeconds, two.landmarkInSeconds);

        if (nowInSeconds - targetLandmark >= RESCALE_THRESHOLD_SECONDS) {
            targetLandmark = nowInSeconds;
        }

        if (targetLandmark != one.landmarkInSeconds) {
            one.rescale(targetLandmark);
        }

        if (targetLandmark != two.landmarkInSeconds) {
            two.rescale(targetLandmark);
        }
    }

    /**
     * Computes the max "weight" of any path starting at node and ending at a leaf in the
     * hypothetical complete tree. The weight is the sum of counts in the ancestors of a given node
     */
    private double computeMaxPathWeight(Node node)
    {
        if (node == null || node.level == 0) {
            return 0;
        }

        double leftMaxWeight = computeMaxPathWeight(node.left);
        double rightMaxWeight = computeMaxPathWeight(node.right);

        return Math.max(leftMaxWeight, rightMaxWeight) + node.weightedCount;
    }

    @VisibleForTesting
    void validate()
    {
        final AtomicDouble sumOfWeights = new AtomicDouble();
        final AtomicInteger actualNodeCount = new AtomicInteger();
        final AtomicInteger actualNonZeroNodeCount = new AtomicInteger();

        if (root != null) {
            validateStructure(root);

            postOrderTraversal(root, new Callback()
            {
                @Override
                public boolean process(Node node)
                {
                    sumOfWeights.addAndGet(node.weightedCount);
                    actualNodeCount.incrementAndGet();

                    if (node.weightedCount >= ZERO_WEIGHT_THRESHOLD) {
                        actualNonZeroNodeCount.incrementAndGet();
                    }

                    return true;
                }
            });
        }

        checkState(Math.abs(sumOfWeights.get() - weightedCount) < ZERO_WEIGHT_THRESHOLD,
                "Computed weight (%s) doesn't match summary (%s)", sumOfWeights.get(),
                weightedCount);

        checkState(actualNodeCount.get() == totalNodeCount,
                "Actual node count (%s) doesn't match summary (%s)",
                actualNodeCount.get(), totalNodeCount);

        checkState(actualNonZeroNodeCount.get() == nonZeroNodeCount,
                "Actual non-zero node count (%s) doesn't match summary (%s)",
                actualNonZeroNodeCount.get(), nonZeroNodeCount);
    }

    private void validateStructure(Node node)
    {
        checkState(node.level >= 0);

        if (node.left != null) {
            validateBranchStructure(node, node.left, node.right, true);
            validateStructure(node.left);
        }

        if (node.right != null) {
            validateBranchStructure(node, node.right, node.left, false);
            validateStructure(node.right);
        }
    }

    private void validateBranchStructure(Node parent, Node child, Node otherChild, boolean isLeft)
    {
        checkState(child.level < parent.level, "Child level (%s) should be smaller than parent level (%s)", child.level, parent.level);

        long branch = child.bits & (1L << (parent.level - 1));
        checkState(branch == 0 && isLeft || branch != 0 && !isLeft, "Value of child node is inconsistent with its branch");

        Preconditions.checkState(parent.weightedCount >= ZERO_WEIGHT_THRESHOLD ||
                child.weightedCount >= ZERO_WEIGHT_THRESHOLD || otherChild != null,
                "Found a linear chain of zero-weight nodes");
    }

    public String toGraphviz()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("digraph QuantileDigest {\n")
                .append("\tgraph [ordering=\"out\"];");

        final List<Node> nodes = new ArrayList<>();
        postOrderTraversal(root, new Callback()
        {
            @Override
            public boolean process(Node node)
            {
                nodes.add(node);
                return true;
            }
        });

        Multimap<Integer, Node> nodesByLevel = Multimaps.index(nodes, input -> input.level);

        for (Map.Entry<Integer, Collection<Node>> entry : nodesByLevel.asMap().entrySet()) {
            builder.append("\tsubgraph level_" + entry.getKey() + " {\n")
                    .append("\t\trank = same;\n");

            for (Node node : entry.getValue()) {
                builder.append(String.format("\t\t%s [label=\"[%s..%s]@%s\\n%s\", shape=rect, style=filled,color=%s];\n",
                        idFor(node),
                        node.getLowerBound(),
                        node.getUpperBound(),
                        node.level,
                        node.weightedCount,
                        node.weightedCount > 0 ? "salmon2" : "white")
                );
            }

            builder.append("\t}\n");
        }

        for (Node node : nodes) {
            if (node.left != null) {
                builder.append(format("\t%s -> %s;\n", idFor(node), idFor(node.left)));
            }
            if (node.right != null) {
                builder.append(format("\t%s -> %s;\n", idFor(node), idFor(node.right)));
            }
        }

        builder.append("}\n");

        return builder.toString();
    }

    private static String idFor(Node node)
    {
        return String.format("node_%x_%x", node.bits, node.level);
    }

    /**
     * Convert a java long (two's complement representation) to a 64-bit lexicographically-sortable binary
     */
    private static long longToBits(long value)
    {
        return value ^ 0x8000_0000_0000_0000L;
    }

    /**
     *  Convert a 64-bit lexicographically-sortable binary to a java long (two's complement representation)
     */
    private static long bitsToLong(long bits)
    {
        return bits ^ 0x8000_0000_0000_0000L;
    }

    public static class Bucket
    {
        private double count;
        private double mean;

        public Bucket(double count, double mean)
        {
            this.count = count;
            this.mean = mean;
        }

        public double getCount()
        {
            return count;
        }

        public double getMean()
        {
            return mean;
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

            final Bucket bucket = (Bucket) o;

            if (Double.compare(bucket.count, count) != 0) {
                return false;
            }
            if (Double.compare(bucket.mean, mean) != 0) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            int result;
            long temp;
            temp = count != +0.0d ? Double.doubleToLongBits(count) : 0L;
            result = (int) (temp ^ (temp >>> 32));
            temp = mean != +0.0d ? Double.doubleToLongBits(mean) : 0L;
            result = 31 * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        public String toString()
        {
            return String.format("[count: %f, mean: %f]", count, mean);
        }
    }

    private static class Node
    {
        private double weightedCount;
        private int level;
        private long bits;
        private Node left;
        private Node right;

        private Node(long bits, int level, double weightedCount)
        {
            this.bits = bits;
            this.level = level;
            this.weightedCount = weightedCount;
        }

        public boolean isLeaf()
        {
            return left == null && right == null;
        }

        public boolean hasSingleChild()
        {
            return left == null && right != null || left != null && right == null;
        }

        public Node getSingleChild()
        {
            checkState(hasSingleChild(), "Node does not have a single child");
            return firstNonNull(left, right);
        }

        public long getUpperBound()
        {
            // set all lsb below level to 1 (we're looking for the highest value of the range covered by this node)
            long mask = 0;

            if (level > 0) { // need to special case when level == 0 because (value >> 64 really means value >> (64 % 64))
                mask = 0xFFFF_FFFF_FFFF_FFFFL >>> (MAX_BITS - level);
            }
            return bitsToLong(bits | mask);
        }

        public long getBranchMask()
        {
            return (1L << (level - 1));
        }

        public long getLowerBound()
        {
            // set all lsb below level to 0 (we're looking for the lowest value of the range covered by this node)
            long mask = 0;

            if (level > 0) { // need to special case when level == 0 because (value >> 64 really means value >> (64 % 64))
                mask = 0xFFFF_FFFF_FFFF_FFFFL >>> (MAX_BITS - level);
            }

            return bitsToLong(bits & (~mask));
        }

        public long getMiddle()
        {
            return getLowerBound() + (getUpperBound() - getLowerBound()) / 2;
        }

        public String toString()
        {
            return format("%s (level = %d, count = %s, left = %s, right = %s)", bits, level, weightedCount, left != null, right != null);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(weightedCount, level, bits, left, right);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final Node other = (Node) obj;
            return Objects.equal(this.weightedCount, other.weightedCount) &&
                    Objects.equal(this.level, other.level) &&
                    Objects.equal(this.bits, other.bits) &&
                    Objects.equal(this.left, other.left) &&
                    Objects.equal(this.right, other.right);
        }
    }

    private static interface Callback
    {
        /**
         * @param node the node to process
         * @return true if processing should continue
         */
        boolean process(Node node);
    }

    private static class SizeOf
    {
        public static final int BYTE = 1;
        public static final int INTEGER = 4;
        public static final int LONG = 8;

        public static final int DOUBLE = 8;

        public static final int QUANTILE_DIGEST = ClassLayout.parseClass(QuantileDigest.class).instanceSize();
        public static final int NODE = ClassLayout.parseClass(Node.class).instanceSize();
    }

    private static class Flags
    {
        public static final int HAS_LEFT = 1 << 0;
        public static final int HAS_RIGHT = 1 << 1;
    }
}

