package io.airlift.http.client.jetty;

import com.google.common.collect.ImmutableList;
import io.airlift.http.client.AbstractHttpClientTest;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.HttpRequestFilter;
import io.airlift.http.client.Request;
import io.airlift.http.client.ResponseHandler;
import io.airlift.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static io.airlift.http.client.Request.Builder.preparePut;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static io.airlift.testing.Closeables.closeQuietly;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

public class TestJettyHttpClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;

    @BeforeMethod
    public void setUp()
    {
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());
        httpClient = new JettyHttpClient(new HttpClientConfig(), jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
    }

    @Override
    @AfterMethod
    public void tearDown()
            throws Exception
    {
        closeQuietly(httpClient);
        closeQuietly(jettyIoPool);
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public <T, E extends Exception> T executeRequest(HttpClientConfig config, Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        try (
                JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
                JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()))
        ) {
            return client.execute(request, responseHandler);
        }
    }

    @Test
    public void testBodyGeneratorException()
            throws Exception
    {
        final Exception testException = new Exception("test exception");
        BlockOnWriteServer blockOnWriteServer = new BlockOnWriteServer();
        try {
            blockOnWriteServer.start();

            Request request = preparePut()
                    .setUri(new URI("http", null, "127.0.0.1", blockOnWriteServer.getLocalPort(), "/", null, null))
                    .setBodyGenerator(new BodyGenerator()
                    {
                        @Override
                        public void write(OutputStream out)
                                throws Exception
                        {
                            // The 16 is unfortunately implementation specific
                            for (int i = 0; i < 16; ++i) {
                                out.write(0);
                            }
                            throw testException;
                        }
                    })
                    .build();
            try {
                executeRequest(request, createStatusResponseHandler());
                fail("Expected test exception");
            }
            catch (RuntimeException e) {
                assertSame(e.getCause(), testException);
            }
        }
        finally {
            blockOnWriteServer.stop();
        }
    }

    private static class BlockOnWriteServer
    {
        private final ServerSocket serverSocket;
        AtomicReference<Socket> connectionSocket = new AtomicReference<>();
        private final ExecutorService executor;
        private Future<?> future;

        private BlockOnWriteServer()
                throws Exception
        {
            serverSocket = new ServerSocket(0);
            executor = Executors.newSingleThreadExecutor();
        }

        public int getLocalPort()
        {
            return serverSocket.getLocalPort();
        }

        public void start()
        {
            future = executor.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    try {
                        Socket connection = serverSocket.accept();
                        connectionSocket.set(connection);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        public void stop()
                throws IOException
        {
            try {
                connectionSocket.get().close();
            }
            catch (IOException | RuntimeException ignored) {
            }
            serverSocket.close();
            executor.shutdown();
            try {
                future.get();
            }
            catch (InterruptedException ignored) {
            }
            catch (ExecutionException e) {
                throw (RuntimeException) e.getCause();
            }
        }
    }
}
