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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.GuardedBy;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class PauseMeter
{
    private static final Logger LOG = Logger.get(PauseMeter.class);

    private final long sleepNanos;

    @GuardedBy("histogram")
    private final Histogram histogram = new Histogram(3);

    @GuardedBy("histogram")
    private long totalPauseNanos;

    private final Supplier<Histogram> snapshot = Suppliers.memoizeWithExpiration(this::makeSnapshot, 1, TimeUnit.SECONDS);

    private final Thread thread;

    // public to make it less likely for the VM to optimize it out
    public volatile Object allocatedObject;

    public PauseMeter()
    {
        this(new Duration(10, TimeUnit.MILLISECONDS));
    }

    public PauseMeter(Duration sleepTime)
    {
        this.sleepNanos = sleepTime.roundTo(TimeUnit.NANOSECONDS);
        thread = new Thread(this::run, "VM Pause Meter");
        thread.setDaemon(true);
    }

    @PostConstruct
    public void start()
    {
        thread.start();
    }

    @PreDestroy
    public void stop()
    {
        thread.interrupt();
    }

    private Histogram makeSnapshot()
    {
        synchronized (histogram) {
            return histogram.copy();
        }
    }

    private void run()
    {
        long shortestObservableInterval = Long.MAX_VALUE;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long before = System.nanoTime();

                TimeUnit.NANOSECONDS.sleep(sleepNanos);

                // attempt to allocate an object to capture any effects due to allocation stalls
                allocatedObject = new Long[] {before};

                long after = System.nanoTime();
                long delta = after - before;

                shortestObservableInterval = Math.min(shortestObservableInterval, delta);

                long pauseNanos = delta - shortestObservableInterval;
                synchronized (histogram) {
                    histogram.recordValue(pauseNanos);
                    totalPauseNanos += pauseNanos;
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Throwable e) {
                LOG.warn(e, "Unexpected error");
            }
        }
    }

    @Managed(description = "< 10ms")
    public long getLessThan10msPauses()
    {
        return snapshot.get().getCountBetweenValues(0, TimeUnit.MILLISECONDS.toNanos(10));
    }

    @Managed(description = "10ms to 50ms")
    public long get10msTo50msPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.MILLISECONDS.toNanos(10), TimeUnit.MILLISECONDS.toNanos(50));
    }

    @Managed(description = "50ms to 500ms")
    public long get50msTo500msPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.MILLISECONDS.toNanos(50), TimeUnit.MILLISECONDS.toNanos(500));
    }

    @Managed(description = "500ms to 1s")
    public long get500msTo1sPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.MILLISECONDS.toNanos(500), TimeUnit.SECONDS.toNanos(1));
    }

    @Managed(description = "1s to 10s")
    public long get1sTo10sPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10));
    }

    @Managed(description = "10s to 1m")
    public long get10sTo1mPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.SECONDS.toNanos(10), TimeUnit.MINUTES.toNanos(1));
    }

    @Managed(description = "> 1m")
    public long getGreaterThan1mPauses()
    {
        return snapshot.get().getCountBetweenValues(TimeUnit.MINUTES.toNanos(1), Long.MAX_VALUE);
    }

    @Managed(description = "Per-bucket counts")
    public Map<Double, Long> getCounts()
    {
        Map<Double, Long> result = new TreeMap<>();
        for (HistogramIterationValue entry : snapshot.get().logarithmicBucketValues(TimeUnit.MILLISECONDS.toNanos(1), 2)) {
            double median = (entry.getValueIteratedTo() + entry.getValueIteratedFrom()) / 2.0;
            result.put(round(median / (double) TimeUnit.MILLISECONDS.toNanos(1), 2), entry.getCountAddedInThisIterationStep());
        }

        return result;
    }

    @Managed(description = "Per-bucket total pause time in s")
    public Map<Double, Double> getSums()
    {
        long previous = 0;
        Map<Double, Double> result = new TreeMap<>();
        for (HistogramIterationValue entry : snapshot.get().logarithmicBucketValues(TimeUnit.MILLISECONDS.toNanos(1), 2)) {
            double median = (entry.getValueIteratedTo() + entry.getValueIteratedFrom()) / 2.0;
            long current = entry.getTotalValueToThisValue();

            result.put(round(median / TimeUnit.MILLISECONDS.toNanos(1), 2), round((current - previous) * 1.0 / TimeUnit.SECONDS.toNanos(1), 2));
            previous = current;
        }

        return result;
    }

    @Managed
    public double getTotalPauseSeconds()
    {
        synchronized (histogram) {
            return totalPauseNanos * 1.0 / TimeUnit.SECONDS.toNanos(1);
        }
    }

    private static double round(double value, int digits)
    {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }
}
