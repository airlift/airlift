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

import com.google.common.base.Ticker;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.airlift.stats.ExponentialDecay.weight;

public class DecayTDigest
{
    private static final long RESCALE_THRESHOLD_SECONDS = 50;
    private static final double ZERO_WEIGHT_THRESHOLD = 1e-5;

    private final TDigest digest;

    private final Ticker ticker;
    private final double alpha;
    private long landmarkInSeconds;

    public DecayTDigest(double compression, double alpha)
    {
        this(new TDigest(compression), alpha, alpha == 0.0 ? noOpTicker() : Ticker.systemTicker());
    }

    public DecayTDigest(double compression, double alpha, Ticker ticker)
    {
        this(new TDigest(compression), alpha, ticker);
    }

    private DecayTDigest(TDigest digest, double alpha, Ticker ticker)
    {
        this(digest, alpha, ticker, TimeUnit.NANOSECONDS.toSeconds(ticker.read()));
    }

    private DecayTDigest(TDigest digest, double alpha, Ticker ticker, long landmarkInSeconds)
    {
        this.digest = digest;
        this.alpha = alpha;
        this.ticker = ticker;
        this.landmarkInSeconds = landmarkInSeconds;
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
        double result = digest.getCount() / weight(alpha, nowInSeconds(), landmarkInSeconds);

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
        if (alpha > 0.0) {
            long nowInSeconds = nowInSeconds();
            if (nowInSeconds - landmarkInSeconds >= RESCALE_THRESHOLD_SECONDS) {
                rescale(nowInSeconds);
            }

            weight *= weight(alpha, nowInSeconds, landmarkInSeconds);
        }

        digest.add(value, weight);
    }

    public double valueAt(double quantile)
    {
        return digest.valueAt(quantile);
    }

    public List<Double> valuesAt(List<Double> quantiles)
    {
        return digest.valuesAt(quantiles);
    }

    private void rescale(long newLandmarkInSeconds)
    {
        // rescale the weights based on a new landmark to avoid numerical overflow issues
        double factor = weight(alpha, newLandmarkInSeconds, landmarkInSeconds);
        digest.totalWeight /= factor;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        int index = 0;
        for (int i = 0; i < digest.centroidCount; i++) {
            double weight = digest.weights[i] / factor;

            if (weight < ZERO_WEIGHT_THRESHOLD) {
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

        landmarkInSeconds = newLandmarkInSeconds;
    }

    private long nowInSeconds()
    {
        return TimeUnit.NANOSECONDS.toSeconds(ticker.read());
    }

    private static Ticker noOpTicker()
    {
        return new Ticker()
        {
            @Override
            public long read()
            {
                return 0;
            }
        };
    }

    public DecayTDigest duplicate()
    {
        return new DecayTDigest(TDigest.copyOf(digest), alpha, ticker, landmarkInSeconds);
    }
}
