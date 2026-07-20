[◀︎ Airlift](../README.md)

## Getting Started

It is assumed that you have Java (minimum version 17), git, and Maven installed. 

## An Airlift Service in 5 Quick Steps

This exercise shows how to create a simple REST server in 5 steps. For a detailed
explanation of everything shown see: 

#### [Getting Started Detailed Explanation](getting_started_explanation.md)

### Step 0 - Create a directory for this test

```
mkdir example
cd example
mkdir -p src/main/java/example
```

### Step 1 - Create your Maven POM

Create `pom.xml` with the following content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>sample-server</groupId>
    <artifactId>sample-server</artifactId>
    <name>sample-server</name>
    <version>1.0-SNAPSHOT</version>

    <parent>
        <groupId>io.airlift</groupId>
        <artifactId>airbase</artifactId>
        <version>110</version>
    </parent>

    <properties>
        <dep.airlift.version>205</dep.airlift.version>
        <air.check.skip-license>true</air.check.skip-license>
        <dep.packaging.version>${dep.airlift.version}</dep.packaging.version>
        <project.build.targetJdk>11</project.build.targetJdk>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.airlift</groupId>
                <artifactId>bom</artifactId>
                <version>${dep.airlift.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>com.google.inject</groupId>
            <artifactId>guice</artifactId>
        </dependency>

        <dependency>
            <groupId>javax.ws.rs</groupId>
            <artifactId>javax.ws.rs-api</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>bootstrap</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>http-server</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>json</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>node</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>event</artifactId>
        </dependency>

        <dependency>
            <groupId>io.airlift</groupId>
            <artifactId>jaxrs</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Step 2 - Create your REST Endpoint

Create a simple JAX-RS REST Resource:

Create `src/main/java/example/ServiceResource.java` with the following content:

```java
package example;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/service")
public class ServiceResource
{
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String hello()
    {
        return "Hello Airlift!";
    }
}
```

### Step 3 - Create your Guice Bindings

Create a Guice Module for your service:

Create `src/main/java/example/ServiceModule.java` with the following content:

```java
package example;

import com.google.inject.Binder;
import com.google.inject.Module;

import static io.airlift.jaxrs.JaxrsBinder.jaxrsBinder;

public class ServiceModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        jaxrsBinder(binder).bind(ServiceResource.class);
    }
}
```

### Step 4 - Create your Main 

Create a Main class for your service:

Create `src/main/java/example/Service.java` with the following content:

```java
package example;

import io.airlift.bootstrap.Bootstrap;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.HttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;

public class Service
{
    public static void main(String[] args)
    {
        Bootstrap app = new Bootstrap(new ServiceModule(),
                new NodeModule(),
                new HttpServerModule(),
                new EventModule(),
                new JsonModule(),
                new JaxrsModule());
        app.initialize();
    }

    private Service()
    {
    }
}
```

### Step 5 - Build and Test

Add the files to git:

```
git init
git add .
git commit -a -m "initial commit"
```

Build the project:

```
mvn clean verify
```

Run the project:

```
mvn exec:java -Dexec.mainClass=example.Service -Dnode.environment=test
```

Test the project:

_In a separate terminal_

```
curl http://localhost:8080/v1/service
```

## Next Steps

- Review the [Getting Started Detailed Explanation](getting_started_explanation.md)
- Go to [Next Steps](next_steps.md) to learn about additional features
