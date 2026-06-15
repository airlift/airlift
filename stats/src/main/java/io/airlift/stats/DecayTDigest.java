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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import jakarta.annotation.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class DecayTDigest
{
    @VisibleForTesting
    static final long RESCALE_THRESHOLD_SECONDS = DecayConfig.RESCALE_THRESHOLD_SECONDS;
    @VisibleForTesting
    static final double ZERO_WEIGHT_THRESHOLD = 1e-5;

    // We scale every weight by this factor to ensure that weights in the underlying
    // digest are >= 1.
    private static final double SCALE_FACTOR = 1 / ZERO_WEIGHT_THRESHOLD;

    private final TDigest digest;
    @Nullable
    private final DecayState decay;

    public DecayTDigest(double compression, double alpha)
    {
        this(compression, alpha == 0.0 ? null : DecayConfig.of(alpha));
    }

    public DecayTDigest(double compression, double alpha, Ticker ticker)
    {
        this(compression, alpha == 0.0 ? null : DecayConfig.of(alpha, ticker));
    }

    /**
     * @param config the decay configuration, or null for a digest that does not decay
     */
    public DecayTDigest(double compression, @Nullable DecayConfig config)
    {
        this(new TDigest(compression), config == null ? null : config.newState());
    }

    private DecayTDigest(TDigest digest, @Nullable DecayState decay)
    {
        this.digest = requireNonNull(digest, "digest is null");
        this.decay = decay;
    }

    public double getMin()
    {
        if (getCount() < ZERO_WEIGHT_THRESHOLD) {
            return Double.NaN;
        }

        return digest.getMin();
    }

    public double getMax()
    {
        if (getCount() < ZERO_WEIGHT_THRESHOLD) {
            return Double.NaN;
        }

        return digest.getMax();
    }

    public double getCount()
    {
        rescaleIfNeeded();

        double result = digest.getCount();

        if (decay != null) {
            result /= (decay.currentWeight() * SCALE_FACTOR);
        }

        if (result < ZERO_WEIGHT_THRESHOLD) {
            result = 0;
        }

        return result;
    }

    public void add(double value)
    {
        add(value, 1);
    }

    public void add(double value, double weight)
    {
        rescaleIfNeeded();

        if (decay != null) {
            weight *= decay.currentWeight() * SCALE_FACTOR;
        }

        digest.add(value, weight);
    }

    private void rescaleIfNeeded()
    {
        if (decay == null) {
            return;
        }
        long nowInSeconds = decay.nowInSeconds();
        if (decay.needsRescale(nowInSeconds)) {
            rescale(nowInSeconds);
        }
    }

    public double valueAt(double quantile)
    {
        return digest.valueAt(quantile);
    }

    public List<Double> valuesAt(List<Double> quantiles)
    {
        return digest.valuesAt(quantiles);
    }

    public double[] valuesAt(double... quantiles)
    {
        return digest.valuesAt(quantiles);
    }

    private void rescale(long newLandmarkInSeconds)
    {
        // rescale the weights based on a new landmark to avoid numerical overflow issues
        double factor = decay.rescaleTo(newLandmarkInSeconds);
        digest.totalWeight /= factor;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        int index = 0;
        for (int i = 0; i < digest.centroidCount; i++) {
            double weight = digest.weights[i] / factor;

            // values with a scaled weight below 1 are effectively below the ZERO_WEIGHT_THRESHOLD
            if (weight < 1) {
                continue;
            }

            digest.weights[index] = weight;
            digest.means[index] = digest.means[i];
            index++;

            min = Math.min(min, digest.means[i]);
            max = Math.max(max, digest.means[i]);
        }

        digest.centroidCount = index;
        digest.min = min;
        digest.max = max;
    }

    public DecayTDigest duplicate()
    {
        return new DecayTDigest(TDigest.copyOf(digest), decay == null ? null : decay.copy());
    }

    public void merge(DecayTDigest other)
    {
        digest.mergeWith(other.digest);
    }
}
