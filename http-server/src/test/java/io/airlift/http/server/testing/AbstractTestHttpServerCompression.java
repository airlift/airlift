package io.airlift.http.server.testing;

import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.http.client.StringResponseHandler;
import io.airlift.http.client.jetty.JettyHttpClient;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.http.server.ServerFeature;
import io.airlift.node.NodeInfo;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTestHttpServerCompression
{
    @Test
    void testCompressionRoundTrip()
            throws Exception
    {
        TestingHttpServer server = createTestingHttpServer(DUMMY_TEXT.substring(0, minCompressionSize()));

        try {
            server.start();
            try (HttpClient client = new JettyHttpClient("test-compression", httpClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .setHeader("Accept-Encoding", acceptEncoding())
                        .build();
                StringResponseHandler.StringResponse execute = client.execute(request, createStringResponseHandler());
                assertThat(execute.getStatusCode()).isEqualTo(SC_OK);
                assertThat(execute.getBody()).isEqualTo(DUMMY_TEXT.substring(0, minCompressionSize()));
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    void testCompressedServerSideButNotSupportedClientSize()
            throws Exception
    {
        TestingHttpServer server = createTestingHttpServer(DUMMY_TEXT.repeat(10));

        try {
            server.start();
            try (HttpClient client = new JettyHttpClient("test-compression", new HttpClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        .setHeader("Accept-Encoding", acceptEncoding())
                        .build();
                StringResponseHandler.StringResponse execute = client.execute(request, createStringResponseHandler());
                assertThat(execute.getStatusCode()).isEqualTo(SC_OK);
                assertThat(execute.getBody().length()).isLessThan(DUMMY_TEXT.length());
                assertThat(execute.getBody()).isNotEqualTo(DUMMY_TEXT.repeat(10));
            }
        }
        finally {
            server.stop();
        }
    }

    @Test
    void testNotCompressedWhenBelowThreshold()
            throws Exception
    {
        TestingHttpServer server = createTestingHttpServer(DUMMY_TEXT.substring(0, minCompressionSize() - 1));

        try {
            server.start();
            // Create client without compression enabled
            try (HttpClient client = new JettyHttpClient("test-compression", new HttpClientConfig())) {
                Request request = prepareGet()
                        .setUri(server.getBaseUrl())
                        // We still accept compression but server should respond with raw data
                        .setHeader("Accept-Encoding", acceptEncoding())
                        .build();
                StringResponseHandler.StringResponse execute = client.execute(request, createStringResponseHandler());
                assertThat(execute.getStatusCode()).isEqualTo(SC_OK);
                assertThat(execute.getBody()).isEqualTo(DUMMY_TEXT.substring(0, minCompressionSize() - 1));
            }
        }
        finally {
            server.stop();
        }
    }

    abstract HttpServerConfig httpServerConfig();

    abstract HttpClientConfig httpClientConfig();

    abstract String acceptEncoding();

    abstract int minCompressionSize();

    private TestingHttpServer createTestingHttpServer(String responseText)
            throws IOException
    {
        NodeInfo nodeInfo = new NodeInfo("test");
        HttpServerConfig config = new HttpServerConfig().setHttpPort(0);
        HttpServerInfo httpServerInfo = new HttpServerInfo(config, nodeInfo);
        return new TestingHttpServer("testing", httpServerInfo, nodeInfo, httpServerConfig().setHttpPort(0), new ContentEncodingServlet(responseText), ServerFeature.defaults());
    }

    private static class ContentEncodingServlet
            extends HttpServlet
    {
        private final String responseText;

        public ContentEncodingServlet(String responseText)
        {
            this.responseText = requireNonNull(responseText, "responseText is null");
        }

        @Override
        public synchronized void init(ServletConfig config)
        {
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        {
            resp.setStatus(SC_OK);
            resp.setContentType("text/poem");

            try {
                resp.getWriter().write(responseText);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final String DUMMY_TEXT = """
        Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut 
        labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris 
        nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit 
        esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, 
        sunt in culpa qui officia deserunt mollit anim id est laborum.""";
}
