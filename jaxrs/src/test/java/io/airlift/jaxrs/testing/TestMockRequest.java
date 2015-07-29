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
package io.airlift.jaxrs.testing;

import com.google.common.collect.ImmutableList;
import io.airlift.jaxrs.testing.MockRequest.ConditionalRequestBuilder;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;

import java.util.Date;
import java.util.Locale;

import static io.airlift.jaxrs.testing.MockRequest.delete;
import static io.airlift.jaxrs.testing.MockRequest.get;
import static io.airlift.jaxrs.testing.MockRequest.head;
import static io.airlift.jaxrs.testing.MockRequest.post;
import static io.airlift.jaxrs.testing.MockRequest.put;
import static java.util.Locale.US;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML_TYPE;

public class TestMockRequest
{
    @DataProvider(name = "requestBuilders")
    private Object[][] getRequestBuilders()
    {
        return new Object[][]{
                {head(), "HEAD", null},
                {get(), "GET", null},
                {post(), "POST", null},
                {put(), "PUT", null},
                {delete(), "DELETE", null},
                {head(VARIANT), "HEAD", VARIANT},
                {get(VARIANT), "GET", VARIANT},
                {post(VARIANT), "POST", VARIANT},
                {put(VARIANT), "PUT", VARIANT},
                {delete(VARIANT), "DELETE", VARIANT},
        };
    }

    public static final EntityTag UNKNOWN_TAG = new EntityTag("unknown");
    public static final EntityTag EXPECTED_TAG = new EntityTag("tag");
    public static final Date BEFORE = new Date(1111);
    public static final Date AFTER = new Date(9999);
    public static final Variant VARIANT = new Variant(TEXT_PLAIN_TYPE, Locale.UK, "UTF-8");
    public static final ImmutableList<Variant> VARIANTS = ImmutableList.of(new Variant(TEXT_XML_TYPE, US, "UTF-8"));

    @Test(dataProvider = "requestBuilders")
    public void testMethod(ConditionalRequestBuilder request, String method, Variant variant)
    {
        Assert.assertEquals(request.unconditionally().getMethod(), method);
    }

    @Test(dataProvider = "requestBuilders")
    public void testSelectVariant(ConditionalRequestBuilder request, String method, Variant variant)
    {
        Assert.assertEquals(request.unconditionally().selectVariant(VARIANTS), variant);
    }

    @Test(dataProvider = "requestBuilders")
    public void testDefaultPreconditions(ConditionalRequestBuilder request, String method, Variant variant)
    {
        assertPreconditionsMet(request.unconditionally().evaluatePreconditions());

        assertPreconditionsMet(request.unconditionally().evaluatePreconditions(BEFORE));

        assertPreconditionsMet(request.unconditionally().evaluatePreconditions(UNKNOWN_TAG));

        assertPreconditionsMet(request.unconditionally().evaluatePreconditions(BEFORE, UNKNOWN_TAG));
    }

    @Test(dataProvider = "requestBuilders")
    public void testIfMatch(ConditionalRequestBuilder requestBuilder, String method, Variant variant)
    {
        assertPreconditionsFailed(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions());

        assertPreconditionsMet(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE));

        assertPreconditionsMet(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(EXPECTED_TAG));
        assertPreconditionsFailed(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(UNKNOWN_TAG));

        assertPreconditionsMet(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsFailed(requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));
    }

    @Test(dataProvider = "requestBuilders")
    public void testIfNoneMatch(ConditionalRequestBuilder requestBuilder, String method, Variant variant)
    {
        assertPreconditionsMet(requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions());

        assertPreconditionsMet(requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE));

        assertIfNoneMatchFailed(method, requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(EXPECTED_TAG));
        assertPreconditionsMet(requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(UNKNOWN_TAG));

        assertIfNoneMatchFailed(method, requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsMet(requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));
    }

    @Test(dataProvider = "requestBuilders")
    public void testIfModifiedSince(ConditionalRequestBuilder requestBuilder, String method, Variant variant)
    {
        assertPreconditionsMet(requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions());

        assertIfModifiedSinceFailed(method, requestBuilder.ifModifiedSince(AFTER).evaluatePreconditions(BEFORE));
        assertPreconditionsMet(requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(AFTER));

        assertPreconditionsMet(requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(EXPECTED_TAG));

        assertIfModifiedSinceFailed(method, requestBuilder.ifModifiedSince(AFTER).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsMet(requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(AFTER, EXPECTED_TAG));
    }

    @Test(dataProvider = "requestBuilders")
    public void testIfUnmodifiedSince(ConditionalRequestBuilder requestBuilder, String method, Variant variant)
    {
        assertPreconditionsMet(requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions());

        assertPreconditionsFailed(requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions(AFTER));
        assertPreconditionsMet(requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(BEFORE));

        assertPreconditionsMet(requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(EXPECTED_TAG));

        assertPreconditionsFailed(requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions(AFTER, EXPECTED_TAG));
        assertPreconditionsMet(requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(BEFORE, EXPECTED_TAG));
    }

    private void assertPreconditionsMet(ResponseBuilder responseBuilder)
    {
        Assert.assertNull(responseBuilder, "Expected null response builder");
    }

    private void assertPreconditionsFailed(ResponseBuilder responseBuilder)
    {
        Assert.assertNotNull(responseBuilder, "Expected a response builder");
        Response response = responseBuilder.build();
        Assert.assertEquals(response.getStatus(), Status.PRECONDITION_FAILED.getStatusCode());
    }

    private void assertIfNoneMatchFailed(String method, ResponseBuilder responseBuilder)
    {
        Assert.assertNotNull(responseBuilder, "Expected a response builder");
        Response response = responseBuilder.build();

        // not modified only applies to GET and HEAD; otherwise it is a precondition failed
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            Assert.assertEquals(response.getStatus(), Status.NOT_MODIFIED.getStatusCode());
        }
        else {
            Assert.assertEquals(response.getStatus(), Status.PRECONDITION_FAILED.getStatusCode());
        }
    }

    private void assertIfModifiedSinceFailed(String method, ResponseBuilder responseBuilder)
    {
        // if modified since only applies to GET and HEAD; otherwise it process request
        if (!("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))) {
            Assert.assertNull(responseBuilder, "Did NOT expect a response builder");
        }
        else {
            Assert.assertNotNull(responseBuilder, "Expected a response builder");
            Response response = responseBuilder.build();

            Assert.assertEquals(response.getStatus(), Status.NOT_MODIFIED.getStatusCode());
        }
    }
}
