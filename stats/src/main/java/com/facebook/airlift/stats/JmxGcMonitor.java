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
package com.facebook.airlift.stats;

import com.facebook.airlift.log.Logger;
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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.max;
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
                        "Major GC: application %sms, stopped %sms: %s -> %s",
                        applicationRuntime,
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
            else if (info.isMinorGc()) {
                minorGc.add(info.getDurationMs(), MILLISECONDS);

                // assumption that minor GCs run currently, so we do not print stopped or application time
                log.debug(
                        "Minor GC: duration %sms: %s -> %s",
                        info.getDurationMs(),
                        info.getBeforeGcTotal(),
                        info.getAfterGcTotal());
            }
        }
    }
}
