package com.facebook.airlift.stats;

import com.google.common.collect.ImmutableMap;
import io.airlift.units.DataSize;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.lang.management.MemoryUsage;
import java.util.Collection;
import java.util.Map;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.airlift.units.DataSize.succinctBytes;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class GarbageCollectionNotificationInfo
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

    public GarbageCollectionNotificationInfo(CompositeData compositeData)
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

    public DataSize getBeforeGcTotal()
    {
        return totalMemorySize(getMemoryUsageBeforeGc());
    }

    public DataSize getAfterGcTotal()
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
