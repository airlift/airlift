[◀︎ Airlift](../README.md) • [◀︎ Getting Started](getting_started.md) • [◀︎ Next Steps](next_steps.md)

## Add Logging

Airlift includes a simple logging API based on the JDK logging package.

### Step 1 - Add Needed Dependencies

We need a few additional dependencies. Add the following to the dependencies section of your
`pom.xml` file:

```xml 
        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>log</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>log-manager</artifactId>
        </dependency>
```

### Step 2 - Start Logging

Example logging:

```java
import io.airlift.log.Logger;

public class MyClass
{
    private static final Logger LOG = Logger.get(MyClass.class);
    
    public void fooBar(String argument)
    {
        LOG.info("Formatted output %s", argument);
    }
}
```

### Additional Configuration

The logging system has several configuration options that are provided to make it easier
to use in cloud native environments.

* `log.path` can be set to the desired output for logging and also provides a mechanism
  to stream logs over TCP. Setting this to the format `tcp://<host>:<port>` will enable the socket
  logger for TCP streaming.

* `log.otlp.enabled` can be set to `true` to export Airlift application logs over OTLP through
  OpenTelemetry. This is intended as a replacement for sending JSON logs over raw TCP to an
  OpenTelemetry Collector `tcplog` receiver.

  Applications using OTLP application log export must install `OpenTelemetryLoggingModule` as a
  top-level `Bootstrap` module. The logging module starts the OTLP log handler during Airlift
  logging initialization:

  ```java
  Bootstrap app = new Bootstrap(
          new OpenTelemetryLoggingModule("my-service", nodeVersion),
          ...);
  ```

  If the application also installs `OpenTelemetryModule`, the Guice-created OpenTelemetry SDK uses
  the logger provider created during logging initialization, so Airlift does not create a second SDK
  logger provider for application logs. `OpenTelemetryExporterModule` is still used for the normal
  trace and metric exporter bindings, and for SDK log export when the early Airlift application log
  handler is not enabled. It is not required for the Airlift application log handler itself.

  Configure the collector endpoint with the normal OpenTelemetry exporter settings. The default
  exporter protocol is gRPC and the default endpoint is `http://localhost:4317`, so a local collector
  using the default OTLP gRPC port only needs:

  ```properties
  node.id=my-service-1
  log.otlp.enabled=true
  ```

  `node.id` must be configured when OTLP application log export is enabled, so early logging uses a
  stable `service.instance.id`.

  For a collector using OTLP HTTP/protobuf, set the base collector URL and protocol:

  ```properties
  node.id=my-service-1
  log.otlp.enabled=true
  otel.exporter.endpoint=http://localhost:4318
  otel.exporter.protocol=http/protobuf
  ```

  The recommended production setup is exporting to a local OpenTelemetry Collector and letting the
  collector forward logs to the backend. OTLP logging uses the OpenTelemetry SDK batch processor,
  which is asynchronous and bounded; configure its queue, batch, schedule, and timeout settings with
  the `otel.exporter.log.*` properties. Logs written before Airlift logging is configured remain
  available through the early console output and are not replayed to OTLP.

* `log.format` can be set to either `text` or `json`. When set to `json`, the log record is formatted as a JSON object, one per line.
  Any newlines in the field values, such as exception stack traces, will be escaped as normal in the JSON object.  This allows for 
  capturing and indexing exceptions as singular fields in a logging search system.

* `node.annotation-file` allows a file containing fields to be set at startup that can be
  interpolated from environment variables and will be included in all log output.

  For example:
  ```
  #config.properties
  node.annotation-file=annotations.properties
  log.format=json
  ```
  ```
  #annotations.properties
  hostIp=${ENV:HOST_IP}
  podName=${ENV:POD_NAME}
  ```
  ```json
  {
    "timestamp": "2021-12-06T16:23:41.352519093Z",
    "level": "DEBUG",
    "thread": "main",
    "logger": "TestLogger",
    "message": "Test Log Message",
    "annotations": {
      "hostIp": "127.0.0.1",
      "podName": "mypod"
    }
  }
  ```
