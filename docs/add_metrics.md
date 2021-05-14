[◀︎ Airlift](../README.md) • [◀︎ Getting Started](getting_started.md) • [◀︎ Next Steps](next_steps.md)

## Add Metrics

Airlift incorporates the [jmxutils](https://github.com/martint/jmxutils) library. Exposing
JMX metrics is very simple.

### Step 1 - Add Needed Dependencies

We need a few additional dependencies. Add the following to the dependencies section of your
`pom.xml` file:

```xml 
<dependency>
    <groupId>org.weakref</groupId>
    <artifactId>jmxutils</artifactId>
</dependency>

<dependency>
    <groupId>io.airlift</groupId>
    <artifactId>jmx</artifactId>
</dependency>
```

### Step 2 - Add the JMX Modules

Edit `Service.java` to look like this:

```java
package example;

import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.jmx.JmxHttpModule;                // NEW
import io.airlift.jmx.JmxModule;                    // NEW
import io.airlift.jmx.http.rpc.JmxHttpRpcModule;    // NEW
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import org.weakref.jmx.guice.MBeanModule;           // NEW

public class Service
{
    public static void main(String[] args)
    {
        Bootstrap app = new Bootstrap(new ServiceModule(),
                new JmxModule(),            // NEW
                new JmxHttpModule(),        // NEW
                new JmxHttpRpcModule(),     // NEW
                new MBeanModule(),          // NEW
                new NodeModule(),
                new HttpServerModule(),
                new EventModule(),
                new JsonModule(),
                new JaxrsModule());
        app.strictConfig().initialize();
    }

    private Service() {}
}
```

### Step 3 - Expose Some Metrics

Let's add a counter to our REST endpoint. Modify `ServiceResource.java` to look like this:

```java
package example;

import org.weakref.jmx.Managed;     // NEW

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.util.concurrent.atomic.AtomicLong;   // NEW

@Path("/v1/service")
public class ServiceResource
{
    private final ServiceConfig config;
    private final AtomicLong helloCount = new AtomicLong();      // NEW

    @Inject
    public ServiceResource(ServiceConfig config)
    {
        this.config = config;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello()
    {
        helloCount.incrementAndGet();   // NEW
        return config.getHelloMessage();
    }

    @Managed                           // NEW
    public long getHelloCount()        // NEW
    {
        return helloCount.get();       // NEW
    }
}
```

### Step 4 - Bind for JMX

Expose the JMX value by binding it. Edit `ServiceModule.java` to look like this:

```java
package example;

import com.google.inject.Binder;
import com.google.inject.Module;

import static io.airlift.configuration.ConfigBinder.configBinder;
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;       // NEW

public class ServiceModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        jaxrsBinder(binder).bind(ServiceResource.class);
        configBinder(binder).bindConfig(ServiceConfig.class);
        newExporter(binder).export(ServiceResource.class).withGeneratedName();  // NEW
    }
}
```

### Step 5 - Test The Change

Build, run, test:

```
mvn clean verify
mvn exec:java -Dexec.mainClass=example.Service -Dnode.environment=test
```

Open `jconsole` or your preferred JMX tool and locate the "example" MBean. You will see the count
increment for each time you `curl http://localhost:8080/v1/service`.
