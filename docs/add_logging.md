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

* `log.output-file` can be set to the desired output for logging and also provides a mechanism
  to stream logs over tcp. Setting this to the format `tcp://<host>:<port>` will enable the socket
  logger for tcp streaming.

* `log.format` can be set to either `text` or `json`. When set to `json`, the log record is formatted as a JSON object, one per line.
  Any newlines in the field values, such as exception stack traces, will be escaped as normal in the JSON object.  This allows for 
  capturing and indexing exceptions as singular fields in a logging search system.

* `log.annotation-file` allows a file containing fields to be set at startup that can be
  interpolated from environment variables and will be included in all log output.

  For example:
  ```
  #config.properties
  log.annotation-file=annotations.properties
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
