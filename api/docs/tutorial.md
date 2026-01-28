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

## Step 2: Add an ApiService

In this step, we'll add our first API service to the server. An API service is a class annotated with `@ApiService` that will contain methods for managing resources. We'll
create a `BookService` for managing books in our bookstore, though it won't have any methods yet.

### What We're Adding

To create an API service, we need three things:
1. An `ApiServiceType` - defines metadata about the service type (ID, version, title, description).
2. An `ApiService` - the class that will contain our API methods.
3. Registration in the server via `ApiModule`.

### The BookServiceType

First, we create a `BookServiceType` that implements the `ApiServiceType` interface:

```java
package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiServiceType;

public class BookServiceType
        implements ApiServiceType
{
    // All endpoints in this service type will have URIs beginning with `<service-type-id>/api/v<service-version-number>/`.

    @Override
    public String id()
    {
        return "bookServiceTypeId";
    }

    @Override
    public int version()
    {
        return 21;
    }

    @Override
    public String title()
    {
        return "Book Service Type";
    }

    @Override
    public String description()
    {
        return "BookServiceType description";
    }
}
```

The `ApiServiceType` provides essential metadata:
- `id`: A unique identifier for this service type. This becomes part of the URI path for all endpoints.
- `version`: The API version number. This allows you to version your API and maintain backward compatibility.
- `title`: A human-readable name for the service type.
- `description`: A description of what this service type does.

All endpoints in services of this type will have URIs that begin with `bookServiceTypeId/api/v21/`.

### The BookService

Next, we create the `BookService` class with the `@ApiService` annotation:

```java
package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiService;

@ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
public class BookService
{
}
```

The `@ApiService` annotation tells API Builder to generate REST endpoints for this service. The annotation parameters specify:
- `name`: The service instance name.
- `type`: The service type (our `BookServiceType` class).
- `description`: What this service does.

Note that the class is currently empty—we haven't added any methods yet. We'll do that in the next step.

### Registering the Service

Finally, we need to register the service with the server. In `BookstoreServer.java`, we add an `ApiModule` after the other modules:

```diff
--- a/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookstoreServer.java
+++ b/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookstoreServer.java
@@ -42,6 +43,11 @@ public class BookstoreServer
                 .add(new JsonModule())
                 .add(new JaxrsModule());

+        // Configure the API module with our book service
+        ApiModule.Builder apiBuilder = ApiModule.builder()
+                .addApi(builder -> builder.add(BookService.class));
+        modules.add(apiBuilder.build());
+
         // Configure server properties
         ImmutableMap.Builder<String, String> serverProperties = ImmutableMap.<String, String>builder()
                 .put("node.environment", "development");
```

The `ApiModule` is responsible for:
- Scanning the `@ApiService` annotated classes.
- Generating JAX-RS resource classes for the API methods.
- Registering those resources with the JAX-RS module.

### Running and Verification

Rebuild and run the server:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer"
```

The server will start successfully, but if you try to access the API:

```bash
curl -f localhost:8080/bookServiceTypeId/api/v21/
```

You'll still get a 404 error. This is expected! While we've registered the service, it doesn't have any methods yet, so there are no endpoints to call. In the next step, we'll 
add methods to create and retrieve books.

## Step 3.1: Add a Service Endpoint

Now that we have an API service registered, let's add our first endpoint. We'll create a method that adds a new books in the bookstore. This method will be invoked when a client
sends an HTTP POST request to the corresponding URI.

### Adding the Create Method

In `BookService.java`, we add a method annotated with `@ApiCreate`:

```diff
--- a/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookService.java
+++ b/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookService.java
@@ -1,8 +1,12 @@
 package io.airlift.api.examples.bookstore;

+import io.airlift.api.ApiCreate;
 import io.airlift.api.ApiService;
+import io.airlift.api.ApiTrait;

 @ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
 public class BookService
 {
+    @ApiCreate(description = "Create a new book", traits = {ApiTrait.BETA})
+    public void createBook() {}
 }
```

The `@ApiCreate` annotation tells API Builder to generate a REST endpoint for creating resources. Key points:
- `description`: Documents what this endpoint does.
- `traits`: Optional metadata about the endpoint. `ApiTrait.BETA` marks this as a beta feature, which can be useful for communicating API stability to clients.

The method is currently empty, but API Builder will generate a complete JAX-RS resource that handles HTTP POST requests. In future steps, we'll add parameters and 
implementation logic.

### Finding the Generated Endpoint URL in the Logs

When the server starts, API Builder logs all registered endpoints to help you discover them. Look for log lines from the `io.airlift.api.binding` logger. They will show the 
HTTP method, full URL, and the corresponding service method. We add a reminder of this to the `main()` method output.

```diff
--- a/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookstoreServer.java
+++ b/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookstoreServer.java
@@ -96,6 +96,8 @@ public class BookstoreServer
         log.info("Bookstore API Server Started");
         log.info("======================================================");
         log.info("Base URI: %s", server.getBaseUri());
+        log.info("");
+        log.info("Please see the io.airlift.api.binding log lines above for available endpoints.");
         log.info("======================================================");
         log.info("Press Ctrl+C to stop the server");
         log.info("======================================================");
```

### Running and Verification

Rebuild and run the server:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer"
```

In the log output, look for lines from `io.airlift.api.binding` that show the registered endpoints. You should see something like:

```
INFO	main	io.airlift.api.binding.JaxrsResourceBuilder	API POST bookServiceTypeId/api/v21/ BookService#createBook
```

This means that a client can create a new book by sending a POST request to the cited URL. Now you can test the endpoint:

```bash
curl -f --json '' localhost:8080/bookServiceTypeId/api/v21/
```

(With `curl`, the `--json` option implies a POST request.)

The request should succeed (returning a 200 status code), though it doesn't do anything yet since the method is empty. But this does mean that the endpoint works and this empty 
method is being invoked. In the next step, we'll make the endpoint actually create a new book inside the server.

## Step 3.2: Add a Book Representation

Now let's create an actual record that represents a book, and update our `createBook` method to accept and store book data.  API Builder calls this a resource, which is a data 
structure annotated with `@ApiResource` that can be sent and received in API requests.  Please see [resources.md](resources.md) for more details.

### Creating the BookData Resource

We create a `BookData` record annotated with `@ApiResource`:

```java
package io.airlift.api.examples.bookstore;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;

import static java.util.Objects.requireNonNull;

@ApiResource(name = "bookData", description = "Book information")
public record BookData(
        @ApiDescription("Title of the book") String title,
        @ApiDescription("Author of the book") String author,
        @ApiDescription("ISBN number") String isbn,
        @ApiDescription("Year published") int year,
        @ApiDescription("Price in USD") double price)
{
    public BookData
    {
        requireNonNull(title, "title is null");
        requireNonNull(author, "author is null");
        requireNonNull(isbn, "isbn is null");

        if (year < 0) {
            throw new IllegalArgumentException("year must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
```

(The reason we call this `BookData` instead of just `Book` will become clear in later steps.)

Key aspects of the resource class:
- `@ApiResource`: Marks this class as an API resource that can be sent/received in API requests. API Builder will handle JSON serialization automatically.
- `@ApiDescription`: Provides mandatory documentation for each field that will appear in generated API documentation.

### Updating the BookService

Now we update `BookService` to actually create and store books:

```diff
--- a/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookService.java
+++ b/api/docs/examples/src/main/java/io/airlift/api/examples/bookstore/BookService.java
@@ -4,9 +4,25 @@ import io.airlift.api.ApiCreate;
 import io.airlift.api.ApiService;
 import io.airlift.api.ApiTrait;

+import java.util.Map;
+import java.util.concurrent.ConcurrentHashMap;
+import java.util.concurrent.atomic.AtomicInteger;
+
+import static io.airlift.api.responses.ApiException.badRequest;
+
 @ApiService(name = "bookService", type = BookServiceType.class, description = "Manage books in the bookstore")
 public class BookService
 {
+    private final AtomicInteger nextId = new AtomicInteger(1);
+    private final Map<String, BookData> books = new ConcurrentHashMap<>();
+
     @ApiCreate(description = "Create a new book", traits = {ApiTrait.BETA})
-    public void createBook() {}
+    public void createBook(BookData bookData)
+    {
+        if (bookData == null) {
+            throw badRequest("Must provide BookData payload");
+        }
+        String id = String.valueOf(nextId.getAndIncrement()); // BookData includes ISBN, but ISBN is neither universal nor actually unique.
+        books.put(id, bookData);
+    }
 }
```

What's changed:
- **Storage**: We add an in-memory store using a map object to hold books.  In a real application, you'd likely use a database instead.
- **Concurrency**: We use `ConcurrentHashMap` and `AtomicInteger` to ensure thread-safe operations since API services may be accessed concurrently.
- **Method parameter**: The `createBook` method now accepts a `BookData` parameter. API Builder will automatically deserialize the JSON request body into a `BookData` object.
- **Validation**: We check if the book data is null and throw a `badRequest` exception if so. This returns an HTTP 400 error to the client.
- **Implementation**: We generate a unique ID and store the book in our map.

Note that although we store the newly created book, we don't yet have a way to retrieve it. We'll add that functionality in a later step.

### Running and Verification

Rebuild and run the server:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="io.airlift.api.examples.bookstore.BookstoreServer"
```

If you look again for the `io.airlift.api.binding` log entry for the `createBook` endpoint, you'll notice that its URL has changed:

```
INFO	main	io.airlift.api.binding.JaxrsResourceBuilder	API POST bookServiceTypeId/api/v21/bookData BookService#createBook
```

This is an important aspect of how API Builder generates endpoints. Since the `createBook` method now accepts a `BookData` parameter, the endpoint URL includes `/bookData` (its 
`@ApiResource` name).  Please see [uris.md](uris.md) for more details on this topic.

Now you can create a book with actual data:

```bash
curl --json '{"title": "The Pragmatic Programmer", "author": "Hunt and Thomas", "isbn": "978-0135957059", "year": 2019, "price": 39.99}' localhost:8080/bookServiceTypeId/api/v21/bookData
```

The request should succeed with a 200 status code. The book is now stored in the service, though we can't retrieve it yet (we'll add an endpoint for that later).

You can also test the validation by sending invalid or incomplete data:

```bash
curl -f --json '{"title": "Missing Fields"}' localhost:8080/bookServiceTypeId/api/v21/bookData
```

This should fail with a 400 Bad Request error because the required fields are missing.
