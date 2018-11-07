/*
 * Copyright 2010 Proofpoint, Inc.
 *
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
/*

Copyright (c) 2010-2011 Coda Hale

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
DEALINGS IN THE SOFTWARE.

Copied from https://github.com/codahale/metrics

*/

package io.airlift.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Math.exp;
import static java.lang.Math.floor;
import static java.lang.Math.min;
import static java.lang.Math.random;

/**
 * An exponentially-decaying random sample of {@code long}s. Uses Cormode et
 * al's forward-decaying priority reservoir sampling method to produce a
 * statistically representative sample, exponentially biased towards newer
 * entries.
 *
 * @see <a href="http://www.research.att.com/people/Cormode_Graham/library/publications/CormodeShkapenyukSrivastavaXu09.pdf">
 * Cormode et al. Forward Decay: A Practical Time Decay Model for Streaming
 * Systems. ICDE '09: Proceedings of the 2009 IEEE International Conference on
 * Data Engineering (2009)</a>
 */
final class ExponentiallyDecayingSample
{
    private static final long RESCALE_THRESHOLD = TimeUnit.HOURS.toNanos(1);
    private final ConcurrentSkipListMap<Double, Long> values;
    private final ReentrantReadWriteLock lock;
    private final double alpha;
    private final int reservoirSize;
    private final AtomicLong count = new AtomicLong(0);
    private volatile long startTime;
    private final AtomicLong nextScaleTime = new AtomicLong(0);

    /**
     * Creates a new {@link ExponentiallyDecayingSample}.
     *
     * @param reservoirSize the number of samples to keep in the sampling reservoir
     * @param alpha the exponential decay factor; the higher this is, the more
     * biased the sample will be towards newer values
     */
    public ExponentiallyDecayingSample(int reservoirSize, double alpha)
    {
        this.values = new ConcurrentSkipListMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.alpha = alpha;
        this.reservoirSize = reservoirSize;
        clear();
    }

    public void clear()
    {
        values.clear();
        count.set(0);
        this.startTime = tick();
        nextScaleTime.set(System.nanoTime() + RESCALE_THRESHOLD);
    }

    public int size()
    {
        return (int) min(reservoirSize, count.get());
    }

    public void update(long value)
    {
        update(value, tick());
    }

    /**
     * Adds an old value with a fixed timestamp to the sample.
     *
     * @param value the value to be added
     * @param timestamp the epoch timestamp of {@code value} in seconds
     */
    public void update(long value, long timestamp)
    {
        lockForRegularUsage();
        try {
            final double priority = weight(timestamp - startTime) / random();
            final long newCount = count.incrementAndGet();
            if (newCount <= reservoirSize) {
                values.put(priority, value);
            }
            else {
                Double first = values.firstKey();
                if (first < priority) {
                    if (values.putIfAbsent(priority, value) == null) {
                        // ensure we always remove an item
                        while (values.remove(first) == null) {
                            first = values.firstKey();
                        }
                    }
                }
            }
        }
        finally {
            unlockForRegularUsage();
        }

        final long now = System.nanoTime();
        final long next = nextScaleTime.get();
        if (now >= next) {
            rescale(now, next);
        }
    }

    public List<Long> values()
    {
        lockForRegularUsage();
        try {
            return new ArrayList<>(values.values());
        }
        finally {
            unlockForRegularUsage();
        }
    }

    private static long tick()
    {
        return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime());
    }

    private double weight(long t)
    {
        return exp(alpha * t);
    }

    /* "A common feature of the above techniques—indeed, the key technique that
     * allows us to track the decayed weights efficiently—is that they maintain
     * counts and other quantities based on g(ti − L), and only scale by g(t − L)
     * at query time. But while g(ti −L)/g(t−L) is guaranteed to lie between zero
     * and one, the intermediate values of g(ti − L) could become very large. For
     * polynomial functions, these values should not grow too large, and should be
     * effectively represented in practice by floating point values without loss of
     * precision. For exponential functions, these values could grow quite large as
     * new values of (ti − L) become large, and potentially exceed the capacity of
     * common floating point types. However, since the values stored by the
     * algorithms are linear combinations of g values (scaled sums), they can be
     * rescaled relative to a new landmark. That is, by the analysis of exponential
     * decay in Section III-A, the choice of L does not affect the final result. We
     * can therefore multiply each value based on L by a factor of exp(−α(L′ − L)),
     * and obtain the correct value as if we had instead computed relative to a new
     * landmark L′ (and then use this new L′ at query time). This can be done with
     * a linear pass over whatever data structure is being used."
     */
    private void rescale(long now, long next)
    {
        if (nextScaleTime.compareAndSet(next, now + RESCALE_THRESHOLD)) {
            lockForRescale();
            try {
                final long oldStartTime = startTime;
                this.startTime = tick();
                final ArrayList<Double> keys = new ArrayList<>(values.keySet());
                for (Double key : keys) {
                    final Long value = values.remove(key);
                    values.put(key * exp(-alpha * (startTime - oldStartTime)), value);
                }
            }
            finally {
                unlockForRescale();
            }
        }
    }

    private void unlockForRescale()
    {
        lock.writeLock().unlock();
    }

    private void lockForRescale()
    {
        lock.writeLock().lock();
    }

    private void lockForRegularUsage()
    {
        lock.readLock().lock();
    }

    private void unlockForRegularUsage()
    {
        lock.readLock().unlock();
    }

    public double[] percentiles(double... percentiles)
    {
        final double[] scores = new double[percentiles.length];
        Arrays.fill(scores, Double.NaN);

        final List<Long> values = this.values();
        if (!values.isEmpty()) {
            Collections.sort(values);

            for (int i = 0; i < percentiles.length; i++) {
                final double p = percentiles[i];
                final double pos = p * (values.size() + 1);
                if (pos < 1) {
                    scores[i] = values.get(0);
                }
                else if (pos >= values.size()) {
                    scores[i] = values.get(values.size() - 1);
                }
                else {
                    final double lower = values.get((int) pos - 1);
                    final double upper = values.get((int) pos);
                    scores[i] = lower + (pos - floor(pos)) * (upper - lower);
                }
            }
        }

        return scores;
    }
}
