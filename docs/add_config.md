[◀︎ Airlift](../README.md) • [◀︎ Getting Started](getting_started.md) • [◀︎ Next Steps](next_steps.md)

## Add Configuration

Airlift's Configuration support is simple and straightforward. Let's add configuration to our
example project.

Currently, the message returned by the `hello()` method is hard coded. Let's make it configurable:

### Step 1 - Add Needed Dependencies

We need a few additional dependencies. Add the following to the dependencies section of your
`pom.xml` file:

```xml 
<dependency>
    <groupId>javax.validation</groupId>
    <artifactId>validation-api</artifactId>
</dependency>

<dependency>
    <groupId>io.airlift</groupId>
    <artifactId>configuration</artifactId>
</dependency>

<dependency>
    <groupId>javax.inject</groupId>
    <artifactId>javax.inject</artifactId>
</dependency>        
```

### Step 2 - Create the Config Class

Create `src/main/java/example/ServiceConfig.java` with the following content:

```java
package example;

import io.airlift.configuration.Config;

import javax.validation.constraints.NotBlank;

public class ServiceConfig
{
    private String helloMessage = "Hello Airlift!";

    @NotBlank
    public String getHelloMessage()
    {
        return helloMessage;
    }

    @Config("hello.message")
    public ServiceConfig setHelloMessage(String helloMessage)
    {
        this.helloMessage = helloMessage;
        return this;
    }
}
```

### Step 3 - Bind the Config Class

We need to bind the Config class in our Guice module. Airlift's `configBinder` is used for this.
Edit your `ServiceModule.java` file so that it looks like this (add the new import and new binding):

```java
package example;

import com.google.inject.Binder;
import com.google.inject.Module;

import static io.airlift.configuration.ConfigBinder.configBinder;   // NEW LINE
import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class ServiceModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        jaxrsBinder(binder).bind(ServiceResource.class);
        configBinder(binder).bindConfig(ServiceConfig.class);   // NEW LINE
    }
}
```

### Step 4 - Use the Config Object 

Modify the Service resource to use the config object. Edit `ServiceResource.java` to look like this:

```java
package example;

import javax.inject.Inject; // NEW
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/service")
public class ServiceResource
{
    private final ServiceConfig config; // NEW

    @Inject                                         // NEW
    public ServiceResource(ServiceConfig config)    // NEW
    {                                               // NEW
        this.config = config;                       // NEW
    }                                               // NEW

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello()
    {
        return config.getHelloMessage();            // CHANGED
    }
}
```

### Step 5 - Test The Change

Build, run, test:

```
mvn clean verify
mvn exec:java -Dexec.mainClass=example.Service -Dnode.environment=test
```

_In a different terminal_ : `curl http://localhost:8080/v1/service`

Then CTRL+C the service.

-----

We assign a default value to `helloMessage` in `ServiceConfig` so Airlift uses that by default. Now
let's run specifying a different value on the command line.

```
mvn exec:java -Dexec.mainClass=example.Service -Dnode.environment=test -Dhello.message=Changed
```

_In a different terminal_ : `curl http://localhost:8080/v1/service`

Then CTRL+C the service.

### Step 6 - Config File

While it's simple to pass configuration on the command line, for production you will use a configuration
properties file. Airlift configuration supports standard field=value property files. 

Create `config.properties` in the root of your project with the following content:

```java
node.environment=test
hello.message=Hello from a config file!
```

Now run and tell Airlift where to find the configuration file:

```java
mvn exec:java -Dexec.mainClass=example.Service -Dconfig=config.properties
```

_In a different terminal_ : `curl http://localhost:8080/v1/service`

Then CTRL+C the service.

## Next Steps

Return to [Next Steps](next_steps.md) to learn about other Airlift features.
