[◀︎ Airlift](../../README.md) • [◀︎ API Builder](../README.md)

# API Builder: A Tutorial

This tutorial will guide you through creating a simple API using API Builder. We will start from a blank slate, where nothing exists yet, and walk through the steps to create a
functional API.

## Following Along with Git

This tutorial has a unique feature: each step corresponds to a git commit in this repository. This allows you to:
- see exactly what changed in each step by viewing the commit diff,
- navigate through the tutorial history using `git log`, and
- check out any step to see the code at that point in time

To view the tutorial commits, use:

```bash
git log tutorial.md
```

Each commit message will indicate which step it represents, making it easy to follow the progression of the bookstore API from a bare-bones server to a full-featured application.

(One imperfection in this plan is that eventually we may need to make changes to earlier steps when Airlift or API Builder evolve. If and when that happens, we will handle it by 
adding new commits on top of the last one, preserving the original history.  That will, however, break the 1-to-1 mapping of tutorial steps to commits, and the diffs from a 
single commit may not be sufficient to see all the changes needed to implement a step.)

## Step 1: Create an Airlift Server Without Any Api Services

In this first step, we'll create the foundation for our API: a bare-bones Airlift server. This server will bootstrap the necessary infrastructure but won't serve any API 
resources yet. Think of it as building the frame of a house before adding the rooms.

By the end of this step, you'll have a running server that accepts HTTP requests, but returns 404 errors, since we haven't defined any endpoints. In subsequent steps, we'll add 
API services to manage a collection of books.

### Project Structure

Our example project lives in the `airlift/api/docs/examples` directory.  It follows the standard Maven layout:

```
docs/examples/
├── pom.xml
└── src/main/java/io/airlift/api/examples/bookstore/
    └── BookstoreServer.java
```

The `pom.xml` defines our project dependencies, including the core Airlift components: `api`, `bootstrap`, `http-server`, `node`, `jaxrs`, `json`, and `log`.  Although housed
inside the Airlift repository for convenience, this example is a standalone Maven project that can be built and run independently, without the rest of the repo content.  For 
that reason, the `pom.xml` includes dependencies on Airlift artifacts via Maven Central.

### The BookstoreServer Class

The `BookstoreServer.java` file contains the main server class that bootstraps our Airlift application. Here's the complete implementation:

```java
package io.airlift.api.examples.bookstore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.log.Logger;
import io.airlift.node.NodeModule;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * Main server class for the bookstore example.
 * This demonstrates how to bootstrap an API using the Airlift framework.
 *
 * To run:
 *   mvn compile exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer"
 *
 * The server will start on port 8080 by default. You can specify a different port as a command-line argument.
 */
public class BookstoreServer
{
    private static final Logger log = Logger.get(BookstoreServer.class);
    private static final int DEFAULT_PORT = 8080;

    private final Injector injector;
    private final URI baseUri;

    public BookstoreServer(int port)
    {
        // Set up the modules needed for the server
        ImmutableList.Builder<Module> modules = ImmutableList.<Module>builder()
                .add(new NodeModule())
                .add(new TestingHttpServerModule(getClass().getName(), port))
                .add(new JsonModule())
                .add(new JaxrsModule());

        // Configure server properties
        ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "development");

        // Bootstrap the application
        Bootstrap app = new Bootstrap(modules.build());
        injector = app.setRequiredConfigurationProperties(serverProperties.build())
                .initialize();

        // Get the server URI
        HttpServerInfo httpServerInfo = injector.getInstance(HttpServerInfo.class);
        baseUri = UriBuilder.fromUri(
                        httpServerInfo.getHttpsUri() != null
                                ? httpServerInfo.getHttpsUri()
                                : httpServerInfo.getHttpUri())
                .host("localhost")
                .build();
    }

    public URI getBaseUri()
    {
        return baseUri;
    }

    public void stop()
    {
        injector.getInstance(LifeCycleManager.class).stop();
    }

    public static void main(String[] args)
    {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        BookstoreServer server = new BookstoreServer(port);

        log.info("======================================================");
        log.info("Bookstore API Server Started");
        log.info("======================================================");
        log.info("Base URI: %s", server.getBaseUri());
        log.info("======================================================");
        log.info("Press Ctrl+C to stop the server");
        log.info("======================================================");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Bookstore API Server...");
            server.stop();
        }));
    }
}
```

#### Understanding the Server Components

**Airlift Modules:**
The server is built by configuring several Airlift modules that provide different capabilities:
- `NodeModule`: Provides node identification and environment configuration for the server.
- `TestingHttpServerModule`: A simplified HTTP server module designed for examples and testing. It accepts a port number parameter. In production, you would typically use `HttpServerModule` instead.
- `JsonModule`: Configures Jackson for JSON serialization and deserialization, which we'll use when handling API requests and responses.
- `JaxrsModule`: Sets up JAX-RS (Jersey) support, which is the standard Java API for RESTful web services. API Builder generates JAX-RS resources behind the scenes.

**Bootstrap Process:**
Airlift uses a Bootstrap pattern to initialize applications:
1. Modules are registered to define what services are available.
2. Properties are set to configure those services (like setting the node environment to "development").
3. Calling `initialize()` starts up all the registered services and wires them together using dependency injection.

### Running the Server

To run the server, you'll need Java 21 and Maven installed. First, compile the project:

```bash
cd docs/examples
mvn compile
```

Then start the server:

```bash
mvn exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer"
```

Optionally, you can specify a different port as an argument:

```bash
mvn exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer" -Dexec.args="9000"
```

To stop the server, press `Ctrl+C`.

### Verification

When the server starts successfully, you should see log output similar to this:

```
======================================================
Bookstore API Server Started
======================================================
Base URI: http://localhost:8080
======================================================
Press Ctrl+C to stop the server
======================================================
```

You can verify the server is running by making a request to it:

```bash
curl -f localhost:8080
```

You should receive a 404 Not Found response. This is expected! The server infrastructure is running and accepting requests (otherwise `curl` would report `Connection refused`), 
but we haven't defined any API endpoints yet. That's what we'll do in the next step.
