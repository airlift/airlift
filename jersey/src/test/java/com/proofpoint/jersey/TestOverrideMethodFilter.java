package com.proofpoint.jersey;

import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.testng.annotations.DataProvider;
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
    @DataProvider(name = "methods")
    private Object[][] getMethods()
    {
        return new Object[][] {
                { "GET" },
                { "POST"},
                { "PUT" },
                { "DELETE" },
                { "HEAD" }
        };
    }
    
    @Test(dataProvider = "methods")
    public void testQueryParamOnPOST(String method)
    {
        assertQueryParamOverridesMethod("POST", method.toUpperCase());
        assertQueryParamOverridesMethod("POST", method.toLowerCase());
    }

    @Test(dataProvider = "methods")
    public void testQueryParamDoesNotOverrideOnGET(String method)
    {
        assertQueryParamThrowsException("GET", method.toUpperCase());
        assertQueryParamThrowsException("GET", method.toLowerCase());
    }

    @Test(dataProvider = "methods")
    public void testQueryParamDoesNotOverrideOnDELETE(String method)
    {
        assertQueryParamThrowsException("DELETE", method.toUpperCase());
        assertQueryParamThrowsException("DELETE", method.toLowerCase());
    }

    @Test(dataProvider = "methods")
    public void testHeaderParamOnPOST(String method)
    {
        assertHeaderOverridesMethod("POST", method.toUpperCase());
        assertHeaderOverridesMethod("POST", method.toLowerCase());
    }

    @Test(dataProvider = "methods")
    public void testHeaderDoesNotOverrideOnGET(String method)
    {
        assertHeaderThrowsException("GET", method.toUpperCase());
        assertHeaderThrowsException("GET", method.toLowerCase());
    }

    @Test(dataProvider = "methods")
    public void testHeaderDoesNotOverrideOnDELETE(String method)
    {
        assertHeaderThrowsException("DELETE", method.toUpperCase());
        assertHeaderThrowsException("DELETE", method.toLowerCase());
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
