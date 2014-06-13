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

import com.google.common.collect.ImmutableList;
import org.glassfish.jersey.internal.PropertiesDelegate;
import org.glassfish.jersey.server.ContainerRequest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import java.net.URI;
import java.security.Principal;
import java.util.Collection;

import static io.airlift.testing.Assertions.assertEqualsIgnoreCase;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestOverrideMethodFilter
{
    @DataProvider(name = "methods")
    private Object[][] getMethods()
    {
        return new Object[][]{
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

        ContainerRequest request = new ContainerRequest(
                URI.create("http://www.example.com/"),
                URI.create("http://www.example.com/"),
                method,
                new MockSecurityContext(),
                new MockPropertiesDelegate());

        filter.filter(request);
        assertEqualsIgnoreCase(request.getMethod(), method);
    }

    @Test
    public void testHeaderHasPrecedenceOverQueryParam()
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        ContainerRequest request = new ContainerRequest(
                URI.create("http://www.example.com/"),
                URI.create("http://www.example.com/?_method=PUT"),
                "POST",
                new MockSecurityContext(),
                new MockPropertiesDelegate());
        request.header(OverrideMethodFilter.HEADER, "DELETE");

        filter.filter(request);
        assertEqualsIgnoreCase(request.getMethod(), "DELETE");

    }

    public static String testQueryParam(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        ContainerRequest request = new ContainerRequest(
                URI.create("http://www.example.com/"),
                URI.create(String.format("http://www.example.com/?_method=%s", override)),
                requestMethod,
                new MockSecurityContext(),
                new MockPropertiesDelegate());

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
        assertEqualsIgnoreCase(resultMethod, override);
    }

    private static String testHeader(String requestMethod, String override)
    {
        OverrideMethodFilter filter = new OverrideMethodFilter();

        ContainerRequest request = new ContainerRequest(
                URI.create("http://www.example.com/"),
                URI.create("http://www.example.com/"),
                requestMethod,
                new MockSecurityContext(),
                new MockPropertiesDelegate());
        request.header("X-HTTP-Method-Override", override);

        filter.filter(request);
        return request.getMethod();
    }

    private static class MockPropertiesDelegate
            implements PropertiesDelegate
    {
        @Override
        public Object getProperty(String name)
        {
            return null;
        }

        @Override
        public Collection<String> getPropertyNames()
        {
            return ImmutableList.of();
        }

        @Override
        public void setProperty(String name, Object object)
        {
        }

        @Override
        public void removeProperty(String name)
        {
        }
    }

    private static class MockSecurityContext
            implements SecurityContext
    {
        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public boolean isUserInRole(String role)
        {
            return false;
        }

        @Override
        public boolean isSecure()
        {
            return false;
        }

        @Override
        public String getAuthenticationScheme()
        {
            return null;
        }
    }
}
