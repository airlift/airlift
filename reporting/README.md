The reporting module is an experimental annotation-based API for
reporting metrics into a [KairosDB](https://code.google.com/p/kairosdb)
time-series database.

Collecting data
===============

Gauges
------

The `@Gauge` annotation may be placed on a getter in order to cause the
attribute to be both reported into the database and exported to JMX through
[jmxutils](https://github.com/martint/jmxutils).

For example:
```java
package com.example;

class ReportedObject
{
    @Gauge
    public int getValue()
    {
       ...
    }
}

public class MyModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        ...
        ReportBinder.reportBinder(binder).export(ReportedObject.class).withGeneratedName();
    }
}
```
will cause the getter to be called every minute and the returned value to be
reported with the metric name `ReportedObject.Value`.
The attribute is also exposed through JMX with the
attribute `Value` of ObjectName `"com.example:name=ReportedObject"`,
just as if `ReportedObject` had been bound with
`ExportBinder.newExporter(binder).export(ReportedObject).withGeneratedName()`.

If the attribute is not to be exported to JMX, the `@Reported`
annotation may be used instead.

A getter annotated with either `@Gauge` or `@Reported` must return either a
number or boolean. Returning `null` causes the metric to not be reported that
minute.

Nested objects
--------------

The `@Nested` and `@Flatten` annotations of jmxutils also work for reporting.

The `@Managed` annotation of jmxutils is ignored for reporting, so may be used
for attributes and methods that should be exposed to JMX only.

Bucketed stats objects
----------------------

The stats objects `CounterStat`, `DistributionStat`, and `TimeStat` report
one-minute bucketed metrics into KairosDB. Their all-time and decayed metrics
are only exported to JMX.

For example:
```java
class ReportedObject
{
    @Flatten
    public CounterStat getCounter()
    {
       ...
    }
}
```
with the same `ReportBinder` call as the first example will result in a
reported metric named `ReportedObject.Count` containing the sum of the values
added to the `CounterStat` that minute. The `CounterStat` will report to JMX
the metrics `TotalCount`, `OneMinute.Count`, `OneMinute.Rate`,
`FiveMinute.Count`, etc. as usual.

Advanced users implementing custom stats objects can extend the `Bucketed`
abstract class in order to report one minute bucketed metrics.

Exporting report objects
------------------------

The `ReportBinder.export()` methods behave like the jmxutils
`ExportBinder.export()` except they cause the bound objects to be exported to
both reporting and JMX.

`ReportExporter` may be used to dynamically export and unexport objects to
both reporting and JMX. It works like the jmxutils `MBeanExporter`.

Report collections
------------------

The `ReportBinder.bindReportCollection()` method may be used to break down
metrics by one or more keys. For example:
```java
public interface StoreStats
{
    CounterStat added(@Key("mediaType") MediaType mediaType,
                      @Key("status") Status status);

    public enum Status {
        SUCCESS, FAILURE;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
};

public class MyModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        ...
        reportBinder.bindReportCollection(StoreStats.class).withGeneratedName();
    }
}
```
will bind an implementation of `StoreStat`. In a corresponding use of that
implementation:
```java
class StoreStatsRecorder {
    private final StoreStats storeStats;
    
    @Inject
    public StoreStatsRecorder(StoreStats storeStats)
    {
        this.storeStats = storeStats;
    }
    
    public void recordSuccessfulAdd(MediaType mediaType)
    {
        storeStats.added(mediaType, SUCCESS)
            .add(1);
    }
}
```
A call to `storeStats.added(mediaType.TEXT_PLAIN, SUCCESS)` will
return a `CounterStat` exported to both reporting and JMX with the name
`"type=StoreStats,name=Added,mediaType=text/plain,status=success"`. Such a
name will result in a metric named `StoreStats.Added.Count` to be reported
with tags `"mediaType=text/plain"` and `"status=success"`.

Alternatively, `ReportCollectionFactory.createReportCollection()` may be used
to dynamically create a report collection implementation.

The interface may have multiple methods. The method return type may be any
class with a public no-arg constructor. The capitalized form of the method
name is used for the "name" property. The `@Key` annotation must be on every
parameter and specifies the tag name. The `.toString()` of parameter's value
is used for the corresponding tag value.

Subsequent calls to the same interface method with parameters that have the
same set of `.toString()` values will result in the same returned object. After
some minutes of a particular returned object not being returned again, the
object will be unexported from reporting and JMX and allowed to be garbage
collected.

Testing report collections
--------------------------

The `TestingReportCollectionFactory` class produces report collection
implementations for use in unit tests.

For any report collection created by `createReportCollection(...)`,
interactions may be verified through objects returned by the following
methods:

`TestingReportCollectionFactory.getArgumentVerifier(...)` returns a mock that
can be used to verify arguments to the report collection methods.

`TestingReportCollectionFactory.getReportCollection(...)` returns an
implementation returning the same values as the one previously created by
`createReportCollection(...)` but which does not affect the argument verifier.
All returned values are Mockito spies, so can have their method calls verified.

```java
class TestStoreStatsRecorder {
    private StoreStatsRecorder storeStatsRecorder;
    private TestingReportCollectionFactory factory;

    @BeforeMethod
    public void setup()
    {
        factory = new TestingReportCollectionFactory();
        storeStatsRecorder = new StoreStatsRecorder(
            factory.createReportCollection(StoreStats.class));
    }

    @Test
    public void testRecordSuccessfulAdd()
    {
        storeStatsRecorder.recordSuccessfulAdd(TEXT_PLAIN);

        verify(factory.getArgumentVerifier(StoreStats.class)).added(TEXT_PLAIN, SUCCESS);
        verifyNoMoreInteractions(factory.getArgumentVerifier(StoreStats.class));

        verify(factory.getReportCollection(StoreStats.class).added(TEXT_PLAIN, SUCCESS)).add(1);
        verifyNoMoreInteractions(factory.getReportCollection(StoreStats.class).added(TEXT_PLAIN, SUCCESS));
    }
}
```

Reporting client
================

`ReportingClientModule` enables reporting of collected data to the time-series
database.

Metric naming
-------------

The "type" and "name" parameters, if present, are prepended to the metric
name, separated by ".", when the metric is reported to KairosDB. For example,
the attribute `Count` in an object exported with type `StoreStat` and name
`Added` will be reported as `StoreStat.Added.Count`.

All other `ObjectName` parameters will be reported as tag/value pairs.

The application name that was supplied to `Bootstrap.bootstrapApplication()`
will be reported as the value of the `application` tag.

Values for the `host`, `environment`, and `pool` tags will be reported from
`NodeInfo`.

Configuration
-------------

  reporting.enabled - Whether to submit metrics to the reporting service.

  reporting.tag - A table of additional tag/value pairs to include in all
                  reported data. For example, `reporting.tag.foo=bar` will include
                  the additional tag `foo=bar`

Sample code
===========

```java
public class HttpServerModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
       // ...
        reportBinder(binder).bindReportCollection(DetailedRequestStats.class).withGeneratedName();
        reportBinder(binder).export(RequestStats.class).withGeneratedName();
    }
}

public interface DetailedRequestStats
{
    TimeStat requestTime(@Key("responseCode") int responseCode);
}

public class RequestStats
{
    private final TimeStat requestTime = new TimeStat();
    private final DistributionStat readBytes = new DistributionStat();
    private final DetailedRequestStats detailedRequestStats;

    @Inject
    public RequestStats(DetailedRequestStats detailedRequestStats)
    {
        this.detailedRequestStats = detailedRequestStats;
    }

    public void record(String method, int responseCode, long requestSizeInBytes, long responseSizeInBytes, Duration schedulingDelay, Duration requestProcessingTime)
    {
        requestTime.add(requestProcessingTime);
        readBytes.add(requestSizeInBytes);
        detailedRequestStats.requestTime(responseCode).add(requestProcessingTime);
    }

    @Nested
    public TimeStat getRequestTime()
    {
        return requestTime;
    }

    @Nested
    public DistributionStat getReadBytes()
    {
        return readBytes;
    }
}
```

