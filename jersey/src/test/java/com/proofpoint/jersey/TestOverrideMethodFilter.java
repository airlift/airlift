package com.proofpoint.jersey;

import com.sun.jersey.core.header.InBoundHeaders;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.WebApplication;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;

import static com.proofpoint.testing.Assertions.assertEqualsIgnoreCase;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;

public class TestOverrideMethodFilter
{
    @Test
    public void testQueryParamOnPOST()
    {
        testQueryParam("POST", "POST", "POST");
        testQueryParam("POST", "GET", "GET");
        testQueryParam("POST", "DELETE", "DELETE");
        testQueryParam("POST", "PUT", "PUT");
    }

    @Test
    public void testQueryParamDoesNotOverrideOnGET()
    {
        testQueryParam("GET", "POST", "GET");
        testQueryParam("GET", "GET", "GET");
        testQueryParam("GET", "DELETE", "GET");
        testQueryParam("GET", "PUT", "GET");
    }

    @Test
    public void testQueryParamDoesNotOverrideOnDELETE()
    {
        testQueryParam("DELETE", "POST", "DELETE");
        testQueryParam("DELETE", "GET", "DELETE");
        testQueryParam("DELETE", "DELETE", "DELETE");
        testQueryParam("DELETE", "PUT", "DELETE");
    }

    @Test
    public void testHeaderParamOnPOST()
    {
        testHeader("POST", "POST", "POST");
        testHeader("POST", "GET", "GET");
        testHeader("POST", "DELETE", "DELETE");
        testHeader("POST", "PUT", "PUT");
    }

    @Test
    public void testHeaderDoesNotOverrideOnGET()
    {
        testHeader("GET", "POST", "GET");
        testHeader("GET", "GET", "GET");
        testHeader("GET", "DELETE", "GET");
        testHeader("GET", "PUT", "GET");
    }

    @Test
    public void testHeaderDoesNotOverrideOnDELETE()
    {
        testHeader("DELETE", "POST", "DELETE");
        testHeader("DELETE", "GET", "DELETE");
        testHeader("DELETE", "DELETE", "DELETE");
        testHeader("DELETE", "PUT", "DELETE");
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
    
    public void testQueryParam(String requestMethod, String override, String expected)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        WebApplication webApp = mock(WebApplication.class, RETURNS_MOCKS);
        ContainerRequest request = new ContainerRequest(webApp,
                                                        requestMethod,
                                                        URI.create("http://www.example.com/"),
                                                        URI.create(String.format("http://www.example.com/?_method=%s", override)),
                                                        new InBoundHeaders(),
                                                        new ByteArrayInputStream(new byte[0]));

        ContainerRequest result = filter.filter(request);
        assertEqualsIgnoreCase(result.getMethod(), expected);
    }

    public void testHeader(String requestMethod, String override, String expected)
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

        ContainerRequest result = filter.filter(request);
        assertEqualsIgnoreCase(result.getMethod(), expected);
    }
}
