/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.jaxrs;

import org.jboss.resteasy.core.interception.jaxrs.PreMatchContainerRequestContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;

import java.net.URI;

import static io.airlift.testing.Assertions.assertEqualsIgnoreCase;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestOverrideMethodFilter
{
    @DataProvider(name = "methods")
    private Object[][] getMethods()
    {
        return new Object[][] {
                {"GET"},
                {"POST"},
                {"PUT"},
                {"DELETE"},
                {"HEAD"}
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

    @Test(dataProvider = "methods")
    public void testRequestUnmodifiedWithNoOverride(String method)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        MockHttpRequest httpRequest = MockHttpRequest.create(
                method,
                URI.create("http://www.example.com/"),
                URI.create("http://www.example.com/"));

        ContainerRequestContext request = createContainerRequest(httpRequest);
        filter.filter(request);
        assertEqualsIgnoreCase(request.getMethod(), method);
    }

    @Test
    public void testHeaderHasPrecedenceOverQueryParam()
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        MockHttpRequest httpRequest = MockHttpRequest.create(
                "POST",
                URI.create("http://www.example.com/?_method=PUT"),
                URI.create("http://www.example.com/"));

        httpRequest.header(OverrideMethodFilter.HEADER, "DELETE");

        ContainerRequestContext request = createContainerRequest(httpRequest);
        filter.filter(request);
        assertEqualsIgnoreCase(request.getMethod(), "DELETE");
    }

    public static String testQueryParam(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        MockHttpRequest httpRequest = MockHttpRequest.create(
                requestMethod,
                URI.create("http://www.example.com/?_method=" + override),
                URI.create("http://www.example.com/"));

        ContainerRequestContext request = createContainerRequest(httpRequest);
        filter.filter(request);
        return request.getMethod();
    }

    private static void assertHeaderThrowsException(String requestMethod, String override)
    {
        try {
            testHeader(requestMethod, override);
            fail("Expected WebApplicationException to be thrown");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        }
    }

    private static void assertHeaderOverridesMethod(String requestMethod, String override)
    {
        String resultMethod = testHeader(requestMethod, override);
        assertEqualsIgnoreCase(resultMethod, override);
    }

    private static void assertQueryParamThrowsException(String requestMethod, String override)
    {
        try {
            testQueryParam(requestMethod, override);
            fail("Expected WebApplicationException to be thrown");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        }
    }

    private static void assertQueryParamOverridesMethod(String requestMethod, String override)
    {
        String resultMethod = testQueryParam(requestMethod, override);
        assertEqualsIgnoreCase(resultMethod, override, "requestMethod=" + requestMethod);
    }

    private static String testHeader(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        MockHttpRequest httpRequest = MockHttpRequest.create(
                requestMethod,
                URI.create("http://www.example.com/"),
                URI.create("http://www.example.com/"));

        httpRequest.header("X-HTTP-Method-Override", override);

        ContainerRequestContext request = createContainerRequest(httpRequest);
        filter.filter(request);
        return request.getMethod();
    }

    private static ContainerRequestContext createContainerRequest(HttpRequest httpRequest)
    {
        return new PreMatchContainerRequestContext(httpRequest, new ContainerRequestFilter[] {}, null);
    }
}
