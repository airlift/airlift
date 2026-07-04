# OpenTelemetry Metrics

The OpenTelemetry metrics exporter converts objects exported through jmxutils into OpenTelemetry
`MetricData`. In normal Airlift applications, metrics are exposed by marking Java objects and
attributes as managed, exporting those objects through jmxutils, and letting the exporter derive
OpenTelemetry metric data from the exported type, managed attribute path, and export identity.

The exporter can also include raw platform MBeans when `metrics.jmx-object-names` is explicitly
configured. Those metrics do not have the same jmxutils export metadata, so they use a separate
best-effort conversion from `ObjectName`.

## Installing

Applications normally install the OpenTelemetry modules in Guice and configure the exporter to send
data to an OpenTelemetry collector:

```java
new Bootstrap(
        new OpenTelemetryModule("trino", nodeVersion),
        new OpenTelemetryExporterModule(),
        new OpenTelemetryMetricsModule(),
        ...);
```

`OpenTelemetryModule` creates the OpenTelemetry SDK objects and resource attributes.
`OpenTelemetryExporterModule` configures OTLP export for traces, metrics, and logs.
`OpenTelemetryMetricsModule` adds the jmxutils managed-metrics producer to the
OpenTelemetry meter provider.

The default exporter protocol is gRPC and the default endpoint is `http://localhost:4317`.
For OTLP HTTP/protobuf, configure the base collector URL and protocol:

```properties
otel.exporter.endpoint=http://otel-collector.example.com:4318
otel.exporter.protocol=http/protobuf
otel.exporter.interval=10s
```

## Native OpenTelemetry Stats

Applications that use OpenTelemetry as their primary metrics path should usually configure the stats
backend before any stats objects are created:

```text
-Dio.airlift.stats.backend=opentelemetry
```

This may also be set from `main` before creating the Airlift bootstrap:

```java
System.setProperty("io.airlift.stats.backend", "opentelemetry");
```

The default Airlift stats backend keeps the traditional Airlift distribution state, including decay
windows for `TimeStat` and `DistributionStat`. When those values are exported to OpenTelemetry,
they are converted to summary metrics containing count, sum, and exported percentiles.

The OpenTelemetry stats backend records distribution values directly into OpenTelemetry-compatible
exponential histograms. The exporter emits those values as OpenTelemetry exponential histogram
metrics instead of summaries. In this mode, `TimeStat` and `DistributionStat` export one native
histogram at the metric name itself; the Airlift backend exports separate summary metrics such as
`.OneMinute`, `.FiveMinutes`, `.FifteenMinutes`, and `.AllTime`.

Example for a managed `TimeStat` attribute named `Time`:

```text
metric name: io.trino.sql.planner.optimizations.PlanOptimizer.Time
metric type: exponential histogram
unit: ns
```

## jmxutils Managed Exports

Managed exports are objects exported through jmxutils `MBeanExporter`. For these metrics, jmxutils
provides both the original export inputs and the final `ObjectName` after the configured
`ObjectNameGenerator` runs.

The OpenTelemetry exporter uses this information as follows:

* The metric name starts with the full exported Java type name.
* The managed attribute path is appended to the metric name with dots. Flattened managed attributes
  omit the flattened getter's Java bean property name, but still include the child attribute names.
  Non-flattened nested attributes include each Java bean property name in the path.
* The final `ObjectName` key properties become OpenTelemetry attributes.
* `type=<simple type name>` is removed from the attributes because the exported type is already
  represented in the metric name.
* A default unqualified `name=<simple type name>` is removed for the same reason.
* An explicit generated name is kept as an attribute because it identifies the exported instance.
* Properties added by an `ObjectNameGenerator`, such as a Trino connector catalog, are kept as
  attributes because they identify the exported instance.

The attribute rule starts from the final `ObjectName` after the configured `ObjectNameGenerator`
runs. The exporter removes only object-kind properties already represented by the metric name, such
as the default `type` or unqualified `name`. All remaining properties are kept as OpenTelemetry
attributes, including properties added by an `ObjectNameGenerator`.

### Default Export

Export:

```java
newExporter(binder).export(NodeInfo.class).withGeneratedName();
```

Export metadata:

```text
exported type: io.airlift.node.NodeInfo
final ObjectName: io.airlift.node:name=NodeInfo
managed attribute path: Environment
```

OpenTelemetry output:

```text
metric name: io.airlift.node.NodeInfo.Environment
attributes: {}
```

The final `name=NodeInfo` property is not emitted as an attribute because it is the default object
kind name.

### Qualified Export

Export:

```java
newExporter(binder)
        .export(HttpClient.class)
        .annotatedWith(ForDiscoveryClient.class)
        .withGeneratedName();
```

Export metadata:

```text
exported type: io.airlift.http.client.HttpClient
final ObjectName: io.airlift.http.client:type=HttpClient,name=ForDiscoveryClient
managed attribute path: AllResponse
```

OpenTelemetry output:

```text
metric name: io.airlift.http.client.HttpClient.AllResponse
attributes:
  name = ForDiscoveryClient
```

The final `type=HttpClient` property is not emitted as an attribute because it is the object kind.
The `name=ForDiscoveryClient` property is emitted because it identifies the qualified binding. In
this example, the managed getter is `getStats()` with `@Flatten`, so the `Stats` property name is not
included in the metric name.

### Nested Attribute Path

For a non-flattened nested managed getter, the Java bean property name is included in the managed
attribute path.

Managed object:

```java
class Server
{
    @Managed
    @Nested
    public ThreadPoolStats getThreadPool()
    {
        return threadPool;
    }
}

class ThreadPoolStats
{
    @Managed
    public int getQueuedRequests()
    {
        return queuedRequests;
    }
}
```

Export metadata:

```text
exported type: io.airlift.http.server.Server
managed attribute path: ThreadPool.QueuedRequests
```

OpenTelemetry output:

```text
metric name: io.airlift.http.server.Server.ThreadPool.QueuedRequests
```

### Property Map Export

Export:

```java
exporter.exportWithGeneratedName(
        optimizerStats,
        PlanOptimizer.class,
        Map.of(
                "name", PlanOptimizer.class.getSimpleName(),
                "optimizer", "PredicatePushDown"));
```

Export metadata:

```text
exported type: io.trino.sql.planner.optimizations.PlanOptimizer
final ObjectName: trino.sql.planner:name=PlanOptimizer,optimizer=PredicatePushDown
original properties: name=PlanOptimizer, optimizer=PredicatePushDown
managed attribute path: Time
```

OpenTelemetry output:

```text
metric name: io.trino.sql.planner.optimizations.PlanOptimizer.Time
attributes:
  optimizer = PredicatePushDown
```

The final `name=PlanOptimizer` property is not emitted as an attribute because the original
properties used it as the object kind name.

### Generator-Added Identity

Trino connector object name generators add catalog identity to the final `ObjectName`.

Export metadata:

```text
exported type: io.trino.plugin.hive.HiveSplitManager
final ObjectName: trino.plugin.hive:type=HiveSplitManager,name=hive,catalog=hive
managed attribute path: QueuedSplits
```

OpenTelemetry output:

```text
metric name: io.trino.plugin.hive.HiveSplitManager.QueuedSplits
attributes:
  catalog = hive
  name = hive
```

The generated `catalog` property is preserved because it distinguishes one connector instance from
another.

## Configured JMX Metrics

Metrics collected from `metrics.jmx-object-names` are read directly from the platform MBean server.
They may not have jmxutils export metadata, so the exporter falls back to the final `ObjectName`:

* If the object name has a `type` property, that is used as the metric base and removed from
  attributes.
* Otherwise, if the object name has a `name` property, that is used as the metric base and removed
  from attributes.
* Otherwise, the object name domain is used as the metric base and all key properties are kept as
  attributes.

Example:

```text
final ObjectName: trino.execution.scheduler:segment=foo
managed attribute path: PlacementFailures
```

OpenTelemetry output:

```text
metric name: trino.execution.scheduler.PlacementFailures
attributes:
  segment = foo
```
