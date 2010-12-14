package com.proofpoint.jersey;

import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.net.URI;

import static com.proofpoint.testing.Assertions.assertEqualsIgnoreCase;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestOverrideMethodFilter
{
    @Test
    public void testQueryParamOnPOST()
    {
        assertQueryParamOverridesMethod("POST", "POST");
        assertQueryParamOverridesMethod("POST", "GET");
        assertQueryParamOverridesMethod("POST", "DELETE");
        assertQueryParamOverridesMethod("POST", "PUT");
    }

    @Test
    public void testQueryParamDoesNotOverrideOnGET()
    {
        assertQueryParamThrowsException("GET", "POST");
        assertQueryParamThrowsException("GET", "GET");
        assertQueryParamThrowsException("GET", "DELETE");
        assertQueryParamThrowsException("GET", "PUT");
    }

    @Test
    public void testQueryParamDoesNotOverrideOnDELETE()
    {
        assertQueryParamThrowsException("DELETE", "POST");
        assertQueryParamThrowsException("DELETE", "GET");
        assertQueryParamThrowsException("DELETE", "DELETE");
        assertQueryParamThrowsException("DELETE", "PUT");
    }

    @Test
    public void testHeaderParamOnPOST()
    {
        assertHeaderOverridesMethod("POST", "POST");
        assertHeaderOverridesMethod("POST", "GET");
        assertHeaderOverridesMethod("POST", "DELETE");
        assertHeaderOverridesMethod("POST", "PUT");
    }

    @Test
    public void testHeaderDoesNotOverrideOnGET()
    {
        assertHeaderThrowsException("GET", "POST");
        assertHeaderThrowsException("GET", "GET");
        assertHeaderThrowsException("GET", "DELETE");
        assertHeaderThrowsException("GET", "PUT");
    }

    @Test
    public void testHeaderDoesNotOverrideOnDELETE()
    {
        assertHeaderThrowsException("DELETE", "POST");
        assertHeaderThrowsException("DELETE", "GET");
        assertHeaderThrowsException("DELETE", "DELETE");
        assertHeaderThrowsException("DELETE", "PUT");
    }

    @Test
    public void testHeaderHasPrecedenceOverQueryParam()
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        WebApplication webApp = mock(WebApplication.class, RETURNS_MOCKS);
        InBoundHeaders headers = new InBoundHeaders();
        headers.add(OverrideMethodFilter.HEADER, "DELETE");
        ContainerRequest request = new ContainerRequest(webApp,
                                                        "POST",
                                                        URI.create("http://www.example.com/"),
                                                        URI.create("http://www.example.com/?_method=PUT"),
                                                        headers,
                                                        new ByteArrayInputStream(new byte[0]));

        ContainerRequest result = filter.filter(request);
        assertEqualsIgnoreCase(result.getMethod(), "DELETE");

    }
    
    public String testQueryParam(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        WebApplication webApp = mock(WebApplication.class, RETURNS_MOCKS);
        ContainerRequest request = new ContainerRequest(webApp,
                                                        requestMethod,
                                                        URI.create("http://www.example.com/"),
                                                        URI.create(String.format("http://www.example.com/?_method=%s", override)),
                                                        new InBoundHeaders(),
                                                        new ByteArrayInputStream(new byte[0]));

        return filter.filter(request).getMethod();
    }

    private void assertHeaderThrowsException(String requestMethod, String override)
    {
        try {
            testHeader(requestMethod, override);
            fail("Expected WebApplicationException to be thrown");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        }
    }

    private void assertHeaderOverridesMethod(String requestMethod, String override)
    {
        String resultMethod = testHeader(requestMethod, override);
        assertEqualsIgnoreCase(resultMethod, override);
    }

    private void assertQueryParamThrowsException(String requestMethod, String override)
    {
        try {
            testQueryParam(requestMethod, override);
            fail("Expected WebApplicationException to be thrown");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        }
    }

    private void assertQueryParamOverridesMethod(String requestMethod, String override)
    {
        String resultMethod = testQueryParam(requestMethod, override);
        assertEqualsIgnoreCase(resultMethod, override);
    }

    private String testHeader(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        WebApplication webApp = mock(WebApplication.class, RETURNS_MOCKS);
        InBoundHeaders headers = new InBoundHeaders();
        headers.add("X-HTTP-Method-Override", override);
        ContainerRequest request = new ContainerRequest(webApp,
                                                        requestMethod,
                                                        URI.create("http://www.example.com/"),
                                                        URI.create("http://www.example.com/"),
                                                        headers,
                                                        new ByteArrayInputStream(new byte[0]));

        return filter.filter(request).getMethod();
    }
}
