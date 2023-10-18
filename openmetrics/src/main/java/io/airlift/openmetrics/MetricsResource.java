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
package io.airlift.openmetrics;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.openmetrics.types.Counter;
import io.airlift.openmetrics.types.Gauge;
import io.airlift.openmetrics.types.Metric;
import io.airlift.openmetrics.types.Summary;
import io.airlift.stats.CounterStat;
import io.airlift.stats.TimeDistribution;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.ManagedClass;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@Path("/metrics")
public class MetricsResource
{
    private static final Logger log = Logger.get(MetricsResource.class);
    private static final String OPENMETRICS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";
    private static final String ATTRIBUTE_SEPARATOR = "_ATTRIBUTE_";
    private static final String TYPE_SEPARATOR = "_TYPE_";
    private static final String NAME_SEPARATOR = "_NAME_";
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("[[a-zA-Z]][\\w_]*");

    private final MBeanServer mbeanServer;
    private final MBeanExporter mbeanExporter;

    private final List<ObjectName> allMetricsObjectNames;

    @Inject
    public MetricsResource(MBeanServer mbeanServer, MBeanExporter mbeanExporter, MetricsConfig metricsConfig)
    {
        this.mbeanServer = requireNonNull(mbeanServer, "mbeanServer is null");
        this.mbeanExporter = requireNonNull(mbeanExporter, "mbeanExporter is null");
        this.allMetricsObjectNames = metricsConfig.getJmxObjectNames();
    }

    @GET
    @Produces(OPENMETRICS_CONTENT_TYPE)
    public String getMetrics(@QueryParam("name[]") List<String> filter)
    {
        StringBuilder body = new StringBuilder();
        if (filter != null && !filter.isEmpty()) {
            for (String metricName : filter) {
                toMetricExposition(metricName).ifPresent(exposition -> body.append(exposition));
            }
        }
        else {
            body.append(managedMetricExpositions());
            for (ObjectName metricObjectNames : allMetricsObjectNames) {
                body.append(jmxMetricExpositions(metricObjectNames));
            }
        }
        body.append("# EOF\n");
        return body.toString();
    }

    private Set<ObjectName> objectNamesFromMetricName(String metricName)
            throws MalformedObjectNameException
    {
        int nameStart = metricName.indexOf(NAME_SEPARATOR);
        int typeStart = metricName.indexOf(TYPE_SEPARATOR);
        int attributeStart = metricName.indexOf(ATTRIBUTE_SEPARATOR);

        String domain;

        if (nameStart != -1) {
            domain = metricName.substring(0, nameStart).replace("_", ".");
        }
        else if (typeStart != -1) {
            domain = metricName.substring(0, typeStart).replace("_", ".");
        }
        else {
            domain = metricName.substring(0, attributeStart).replace("_", ".");
        }

        StringBuilder objectNameBuilder = new StringBuilder(domain).append(":");

        if (nameStart != -1) {
            objectNameBuilder.append("name=")
                    .append(metricName, nameStart + NAME_SEPARATOR.length(), typeStart == -1 ? attributeStart : typeStart)
                    .append(",");
        }
        if (typeStart != -1) {
            objectNameBuilder.append("type=")
                    .append(metricName.substring(typeStart + TYPE_SEPARATOR.length(), attributeStart).replace("_", "$"))
                    .append(",");
        }

        return mbeanServer.queryNames(ObjectName.getInstance(objectNameBuilder.append("*").toString()), null);
    }

    private String attributeNameFromMetricName(String metricName)
    {
        int attributeNameStart = metricName.indexOf(ATTRIBUTE_SEPARATOR);
        if (attributeNameStart == -1) {
            throw new RuntimeException("Metric name invalid, no attribute separator %s".formatted(metricName));
        }
        return metricName.substring(attributeNameStart + ATTRIBUTE_SEPARATOR.length()).replace("_", ".");
    }

    private String mBeanNameToMetricName(ObjectName objectName, String attributeName)
    {
        if (objectName.getDomain().contains("_")) {
            log.warn("Unable to expose JMX metric with domain name %s, package names with underscores are unsupported.", objectName.getDomain());
            throw new RuntimeException("Bad domain name %s".formatted(objectName.getDomain()));
        }

        StringBuilder metricNameBuilder = new StringBuilder("JMX_")
                .append(objectName.getDomain().replace(".", "_"));

        if (objectName.getKeyProperty("name") != null) {
            metricNameBuilder.append(NAME_SEPARATOR)
                    .append(objectName.getKeyProperty("name"));
        }

        if (objectName.getKeyProperty("type") != null) {
            metricNameBuilder.append(TYPE_SEPARATOR)
                    .append(objectName.getKeyProperty("type").replace("$", "_"));
        }

        metricNameBuilder.append(ATTRIBUTE_SEPARATOR)
                .append(attributeName.replace(".", "_"));

        String metricName = metricNameBuilder.toString();

        if (!METRIC_NAME_PATTERN.matcher(metricName).matches()) {
            log.warn("Calculated metric name has invalid characters %s skipping", metricName);
        }
        return metricName;
    }

    private Optional<String> toMetricExposition(String metricName)
    {
        if (metricName.startsWith("JMX_")) {
            final String jmxMetricName = metricName.substring(4);
            try {
                String attributeName = attributeNameFromMetricName(jmxMetricName);
                return objectNamesFromMetricName(jmxMetricName).stream()
                        .map(objectName -> getMetric(objectName, attributeName, jmxMetricName, ""))
                        .flatMap(Optional::stream)
                        .map(Metric::getMetricExposition)
                        .findFirst();
            }
            catch (MalformedObjectNameException e) {
                log.warn(e, "Unable to retrieve metric %s.", metricName);
                return Optional.empty();
            }
        }
        else {
            Stream<Metric> metricStream = getManagedMetricsStream();
            return metricStream
                    .filter(metric -> metric.metricName().equals(metricName))
                    .findFirst()
                    .map(Metric::getMetricExposition);
        }
    }

    private Optional<Metric> getMetric(ObjectName objectName, String attributeName, String metricName, String description)
    {
        try {
            Object attributeValue = mbeanServer.getAttribute(objectName, attributeName);

            if (attributeValue == null) {
                return Optional.empty();
            }

            if (attributeValue instanceof Number) {
                return Optional.of(Gauge.from(metricName, (Number) attributeValue, description));
            }

            return Optional.empty();
        }
        catch (JMException ex) {
            log.debug(ex, "Unable to get metric for ObjectName %s and Attribute %s.", objectName.getCanonicalName(), attributeName);
            return Optional.empty();
        }
    }

    private String inferAttributesForObjectName(ObjectName objectName)
    {
        StringBuilder expositions = new StringBuilder();
        try {
            MBeanInfo mbeanInfo = mbeanServer.getMBeanInfo(objectName);
            for (MBeanAttributeInfo mBeanAttributeInfo : mbeanInfo.getAttributes()) {
                String attributeName = mBeanAttributeInfo.getName();
                String description = mBeanAttributeInfo.getDescription();
                try {
                    getMetric(objectName, attributeName, mBeanNameToMetricName(objectName, attributeName), description)
                            .ifPresent(value -> expositions.append(value.getMetricExposition()));
                }
                catch (RuntimeException e) {
                    log.debug(e, "Unable to get Metric for ObjectName %s and Attribute %s, skipping", objectName.getCanonicalName(), attributeName);
                }
            }
        }
        catch (InstanceNotFoundException | IntrospectionException | ReflectionException e) {
            log.debug(e, "Unable to get MBeanInfo for object %s, skipping", objectName.getCanonicalName());
        }
        return expositions.toString();
    }

    private String sanitizeMetricName(String name)
    {
        return name.replace(".", "_")
                .replace("$", "_")
                .replace(":", "_")
                .replace("=", "_")
                .replace(",", "_");
    }

    private List<Metric> getMetricsRecursively(String prefix, ManagedClass managedClass)
    {
        String metricName = sanitizeMetricName(prefix);

        ImmutableList.Builder<Metric> metrics = ImmutableList.builder();

        for (String attributeName : managedClass.getAttributeNames()) {
            try {
                String metricAndAttribute = managedClass.isAttributeFlatten(attributeName) ? metricName : metricName + "_" + attributeName;
                String attributeDescription = managedClass.getAttributeDescription(attributeName);

                ManagedClass child = managedClass.getChildren().get(attributeName);
                if (child != null) {
                    // The managed class is directly translatable to an openmetrics type, don't recurse any further
                    Optional<Metric> metricFromTarget = getMetricFromTarget(child, metricAndAttribute, attributeDescription);
                    if (metricFromTarget.isPresent()) {
                        metrics.add(metricFromTarget.get());
                    }
                    else {
                        // Recurse this nested child
                        metrics.addAll(getMetricsRecursively(metricAndAttribute, child));
                    }
                }
                else {
                    // Attempt to infer a numeric gauge
                    Object attributeValue = managedClass.invokeAttribute(attributeName);
                    if (attributeValue instanceof Number) {
                        metrics.add(Gauge.from(metricAndAttribute, (Number) attributeValue, attributeDescription));
                    }
                }
            }
            catch (ReflectiveOperationException e) {
                log.debug("Unable to invoke getter for managed attribute : " + attributeName);
            }
        }

        return metrics.build();
    }

    private Optional<Metric> getMetricFromTarget(ManagedClass managedClass, String metricName, String description)
    {
        Object target;
        try {
            target = managedClass.getTarget();
        }
        catch (IllegalStateException ignored) {
            return Optional.empty();
        }

        if (target instanceof CounterStat counterStat) {
            return Optional.of(Counter.from(metricName, counterStat, description));
        }

        if (target instanceof TimeDistribution timeDistribution) {
            return Optional.of(Summary.from(metricName, timeDistribution, description));
        }

        return Optional.empty();
    }

    private String jmxMetricExpositions(ObjectName initialObjectName)
    {
        StringBuilder stringBuilder = new StringBuilder();

        mbeanServer.queryNames(initialObjectName, null).forEach(objectName -> stringBuilder.append(inferAttributesForObjectName(objectName)));

        return stringBuilder.toString();
    }

    private Stream<Metric> getManagedMetricsStream()
    {
        Map<String, ManagedClass> managedClasses = this.mbeanExporter.getManagedClasses();

        return managedClasses.keySet().stream()
                .map(objectName -> getMetricsRecursively(objectName, managedClasses.get(objectName)))
                .flatMap(List::stream);
    }

    private String managedMetricExpositions()
    {
        StringBuilder builder = new StringBuilder();

        getManagedMetricsStream().forEach(metric -> builder.append(metric.getMetricExposition()));

        return builder.toString();
    }
}
