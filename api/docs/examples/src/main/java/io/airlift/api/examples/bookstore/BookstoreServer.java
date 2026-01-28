package io.airlift.api.examples.bookstore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.airlift.api.binding.ApiModule;
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

        // Configure the API module with our book service
        ApiModule.Builder apiBuilder = ApiModule.builder()
                .addApi(builder -> builder.add(BookService.class));
        modules.add(apiBuilder.build());

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
        log.info("");
        log.info("Please see the io.airlift.api.binding log lines above for available endpoints.");
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
