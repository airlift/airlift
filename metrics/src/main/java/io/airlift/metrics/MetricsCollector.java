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
package io.airlift.metrics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.metrics.MetricSource.JmxMetricSource;
import io.airlift.metrics.MetricSource.ManagedMetricSource;
import io.airlift.node.NodeInfo;
import io.airlift.stats.CounterStat;
import io.airlift.stats.Distribution;
import io.airlift.stats.DistributionStat;
import io.airlift.stats.TimeDistribution;
import io.airlift.stats.TimeStat;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.ManagedClass;
import org.weakref.jmx.ManagedObjectExport;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

/// Discovers Airlift managed metrics and configured JMX metrics in their native form.
public class MetricsCollector
{
    private static final Logger log = Logger.get(MetricsCollector.class);

    private final MBeanServer mbeanServer;
    private final MBeanExporter mbeanExporter;
    private final List<ObjectName> allMetricsObjectNames;
    private final Map<String, String> labels;

    @Inject
    public MetricsCollector(MBeanServer mbeanServer, MBeanExporter mbeanExporter, MetricsConfig metricsConfig, NodeInfo nodeInfo)
    {
        this.mbeanServer = requireNonNull(mbeanServer, "mbeanServer is null");
        this.mbeanExporter = requireNonNull(mbeanExporter, "mbeanExporter is null");
        requireNonNull(metricsConfig, "metricsConfig is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        this.allMetricsObjectNames = ImmutableList.copyOf(metricsConfig.getJmxObjectNames());
        this.labels = ImmutableMap.copyOf(nodeInfo.getAnnotations());
    }

    public List<CollectedMetricGroup> collect()
    {
        Collection<ManagedObjectExport> managedExports = mbeanExporter.getManagedObjectExports().values();
        Set<ObjectName> managedObjectNames = managedExports.stream()
                .map(ManagedObjectExport::getObjectName)
                .collect(toImmutableSet());

        return ImmutableList.<CollectedMetricGroup>builder()
                .addAll(collectManagedClasses(managedExports))
                .addAll(collectMBeans(managedObjectNames))
                .build();
    }

    private List<CollectedMetricGroup> collectManagedClasses(Collection<ManagedObjectExport> managedExports)
    {
        return managedExports.stream()
                .map(export -> new CollectedMetricGroup(
                        managedMetricSource(export),
                        labels,
                        collectManagedClassAttributes(List.of(), export.getManagedClass())))
                .filter(group -> !group.attributes().isEmpty())
                .collect(toImmutableList());
    }

    private static ManagedMetricSource managedMetricSource(ManagedObjectExport export)
    {
        return new ManagedMetricSource(
                export.getObjectName().toString(),
                export.getExportedType(),
                export.getOriginalName(),
                export.getOriginalProperties());
    }

    private static List<CollectedMetricGroup.Attribute> collectManagedClassAttributes(List<String> path, ManagedClass managedClass)
    {
        ImmutableList.Builder<CollectedMetricGroup.Attribute> metrics = ImmutableList.builder();

        Map<String, ManagedClass> children = managedClass.getChildren();
        for (String attributeName : managedClass.getAttributeNames()) {
            List<String> attributePath = managedClass.isAttributeFlatten(attributeName) ? path : append(path, attributeName);
            try {
                String attributeDescription = managedClass.getAttributeDescription(attributeName);

                ManagedClass child = children.get(attributeName);
                if (child != null) {
                    try {
                        Object target = child.getTarget();
                        if (isRawStat(target)) {
                            metrics.add(new CollectedMetricGroup.Attribute(attributePath, target, attributeDescription));
                            continue;
                        }
                    }
                    catch (IllegalStateException _) {
                    }
                    metrics.addAll(collectManagedClassAttributes(attributePath, child));
                }
                else {
                    Object attributeValue = managedClass.invokeAttribute(attributeName);
                    if (attributeValue instanceof Number || attributeValue instanceof Boolean) {
                        metrics.add(new CollectedMetricGroup.Attribute(attributePath, attributeValue, attributeDescription));
                    }
                }
            }
            catch (ReflectiveOperationException e) {
                log.debug(e, "Unable to invoke getter for managed attribute %s on %s, skipping", String.join(".", attributePath), managedClass.getTargetClass().getName());
            }
        }

        return metrics.build();
    }

    private static boolean isRawStat(Object value)
    {
        return value instanceof CounterStat ||
                value instanceof TimeDistribution ||
                value instanceof TimeStat ||
                value instanceof Distribution ||
                value instanceof DistributionStat;
    }

    private List<CollectedMetricGroup> collectMBeans(Set<ObjectName> managedObjectNames)
    {
        return allMetricsObjectNames.stream()
                .map(objectName -> mbeanServer.queryNames(objectName, null))
                .flatMap(Set::stream)
                .distinct()
                .filter(objectName -> !managedObjectNames.contains(objectName))
                .map(this::collectMBean)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CollectedMetricGroup> collectMBean(ObjectName objectName)
    {
        ImmutableList.Builder<CollectedMetricGroup.Attribute> attributes = ImmutableList.builder();
        try {
            MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(objectName);
            for (MBeanAttributeInfo mBeanAttributeInfo : mbeanInfo.getAttributes()) {
                collectMBeanAttribute(objectName, mBeanAttributeInfo.getName(), mBeanAttributeInfo.getDescription())
                        .ifPresent(attributes::add);
            }
        }
        catch (InstanceNotFoundException | IntrospectionException | ReflectionException e) {
            log.debug(e, "Unable to get MBeanInfo for object %s, skipping", objectName.getCanonicalName());
        }

        List<CollectedMetricGroup.Attribute> collectedAttributes = attributes.build();
        if (collectedAttributes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CollectedMetricGroup(new JmxMetricSource(objectName), labels, collectedAttributes));
    }

    private Optional<CollectedMetricGroup.Attribute> collectMBeanAttribute(ObjectName objectName, String attributeName, String description)
    {
        try {
            Object attributeValue = mbeanServer.getAttribute(objectName, attributeName);
            return switch (attributeValue) {
                case Number _, Boolean _, CompositeData _, TabularData _ -> Optional.of(new CollectedMetricGroup.Attribute(List.of(attributeName), attributeValue, description));
                case null, default -> Optional.empty();
            };
        }
        catch (JMException | RuntimeException e) {
            log.debug(e, "Unable to get Metric for ObjectName %s and Attribute %s, skipping", objectName.getCanonicalName(), attributeName);
            return Optional.empty();
        }
    }

    private static List<String> append(List<String> path, String attributeName)
    {
        return ImmutableList.<String>builderWithExpectedSize(path.size() + 1)
                .addAll(path)
                .add(attributeName)
                .build();
    }
}
