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

import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.concurrent.GuardedBy;
import javax.management.JMException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.units.DataSize.succinctBytes;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitor GC events via JMX. GC events are divided into major and minor using
 * the OpenJDK naming convention for gcAction.  Also, application time is calculated
 * using the assumption that major collections stop the application.
 * <p>
 * Major and minor GCs are logged to standard logging system, which makes it
 * easy to debug the full log stream. TimeStats are exported for major, minor,
 * and application time.
 */
public class JmxGcMonitor
        implements GcMonitor
{
    private final Logger log = Logger.get(JmxGcMonitor.class);

    private final NotificationListener notificationListener = (notification, ignored) -> onNotification(notification);

    private final AtomicLong majorGcCount = new AtomicLong();
    private final AtomicLong majorGcTime = new AtomicLong();
    private final TimeStat majorGc = new TimeStat();

    private final TimeStat minorGc = new TimeStat();

    @GuardedBy("this")
    private long lastGcEndTime = System.currentTimeMillis();

    @PostConstruct
    public void start()
    {
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ObjectName objectName = mbean.getObjectName();
            try {
                ManagementFactory.getPlatformMBeanServer().addNotificationListener(
                        objectName,
                        notificationListener,
                        null,
                        null);
            }
            catch (JMException e) {
                throw new RuntimeException("Unable to add GC listener", e);
            }
        }
    }

    @PreDestroy
    public void stop()
    {
        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            ObjectName objectName = mbean.getObjectName();
            try {
                ManagementFactory.getPlatformMBeanServer().removeNotificationListener(objectName, notificationListener);
            }
            catch (JMException ignored) {
            }
        }
    }

    @Override
    public long getMajorGcCount()
    {
        return majorGcCount.get();
    }

    @Override
    public Duration getMajorGcTime()
    {
        return new Duration(majorGcTime.get(), MILLISECONDS);
    }

    @Managed
    @Nested
    public TimeStat getMajorGc()
    {
        return majorGc;
    }

    @Managed
    @Nested
    public TimeStat getMinorGc()
    {
        return minorGc;
    }

    private synchronized void onNotification(Notification notification)
    {
        if ("com.sun.management.gc.notification".equals(notification.getType())) {
            GarbageCollectionNotificationInfo info = new GarbageCollectionNotificationInfo((CompositeData) notification.getUserData());

            if (info.isMajorGc()) {
                majorGcCount.incrementAndGet();
                majorGcTime.addAndGet(info.getDurationMs());
                majorGc.add(info.getDurationMs(), MILLISECONDS);

                // assume that major GCs stop the application
                long applicationRuntime = max(0, info.getStartTime() - lastGcEndTime);
                lastGcEndTime = info.getEndTime();

                log.info(
                        "Major GC: application %sms, stopped %sms:: %s -> %s",
                        applicationRuntime,
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
            else if (info.isMinorGc()) {
                minorGc.add(info.getDurationMs(), MILLISECONDS);

                // assumption that minor GCs run currently, so we do not print stopped or application time
                log.debug(
                        "Minor GC: duration %sms:: %s -> %s",
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
        }
    }

    private static class GarbageCollectionNotificationInfo
    {
        // these are well known constants used by all known OpenJDK GCs
        private static final String MINOR_GC_NAME = "end of minor GC";
        private static final String MAJOR_GC_NAME = "end of major GC";

        private final String gcName;
        private final String gcAction;
        private final String gcCause;

        private final long startTime;
        private final long endTime;
        private final Map<String, MemoryUsage> usageBeforeGc;
        private final Map<String, MemoryUsage> usageAfterGc;

        GarbageCollectionNotificationInfo(CompositeData compositeData)
        {
            requireNonNull(compositeData, "compositeData is null");
            this.gcName = (String) compositeData.get("gcName");
            this.gcAction = (String) compositeData.get("gcAction");
            this.gcCause = (String) compositeData.get("gcCause");

            CompositeData gcInfo = (CompositeData) compositeData.get("gcInfo");
            this.startTime = (Long) gcInfo.get("startTime");
            this.endTime = (Long) gcInfo.get("endTime");
            this.usageBeforeGc = extractMemoryUsageMap(gcInfo, "memoryUsageBeforeGc");
            this.usageAfterGc = extractMemoryUsageMap(gcInfo, "memoryUsageAfterGc");
        }

        public long getStartTime()
        {
            return startTime;
        }

        public long getEndTime()
        {
            return endTime;
        }

        public long getDurationMs()
        {
            return max(0, this.endTime - this.startTime);
        }

        public Map<String, MemoryUsage> getMemoryUsageBeforeGc()
        {
            return usageBeforeGc;
        }

        public Map<String, MemoryUsage> getMemoryUsageAfterGc()
        {
            return usageAfterGc;
        }

        private DataSize getBeforeGcTotal()
        {
            return totalMemorySize(getMemoryUsageBeforeGc());
        }

        private DataSize getAfterGcTotal()
        {
            return totalMemorySize(getMemoryUsageAfterGc());
        }

        public boolean isMinorGc()
        {
            return gcAction.equalsIgnoreCase(MINOR_GC_NAME);
        }

        public boolean isMajorGc()
        {
            return gcAction.equalsIgnoreCase(MAJOR_GC_NAME);
        }

        @Override
        public String toString()
        {
            return toStringHelper(this)
                    .add("gcName", gcName)
                    .add("gcAction", gcAction)
                    .add("gcCause", gcCause)
                    .add("durationMs", getDurationMs())
                    .add("beforeGcMb", getBeforeGcTotal())
                    .add("afterGcMb", getAfterGcTotal())
                    .toString();
        }

        private static DataSize totalMemorySize(Map<String, MemoryUsage> memUsages)
        {
            long bytes = 0;
            for (MemoryUsage memoryUsage : memUsages.values()) {
                bytes += memoryUsage.getUsed();
            }
            return succinctBytes(bytes);
        }

        private static Map<String, MemoryUsage> extractMemoryUsageMap(CompositeData compositeData, String attributeName)
        {
            ImmutableMap.Builder<String, MemoryUsage> map = ImmutableMap.builder();
            TabularData tabularData = (TabularData) compositeData.get(attributeName);
            for (CompositeData entry : (Collection<CompositeData>) tabularData.values()) {
                map.put((String) entry.get("key"), MemoryUsage.from((CompositeData) entry.get("value")));
            }
            return map.build();
        }
    }
}
