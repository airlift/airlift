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
package com.proofpoint.jaxrs.testing;

import com.google.common.collect.ImmutableList;
import junit.framework.TestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;

import java.util.Date;
import java.util.Locale;

import static com.proofpoint.jaxrs.testing.MockRequest.mockRequest;
import static java.util.Locale.US;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_XML_TYPE;

public class TestMockRequest extends TestCase
{
    public static final EntityTag UNKNOWN_TAG = new EntityTag("unknown");
    public static final EntityTag EXPECTED_TAG = new EntityTag("tag");
    public static final Date BEFORE = new Date(1111);
    public static final Date AFTER = new Date(9999);

    @Test
    public void testMethod()
    {
        Assert.assertEquals(mockRequest().method("GET").getMethod(), "GET");
    }

    @Test
    public void testSelectVariant()
    {
        Variant variant = new Variant(TEXT_PLAIN_TYPE, Locale.UK, "UTF-8");
        Assert.assertEquals(mockRequest().setSelectVariant(variant).selectVariant(ImmutableList.of(new Variant(TEXT_XML_TYPE, US, "UTF-8"))), variant);
    }

    @Test
    public void testDefaultPreconditions()
    {
        assertPreconditionsMet(mockRequest().evaluatePreconditions(UNKNOWN_TAG));
        assertPreconditionsMet(mockRequest().evaluatePreconditions(new Date(1000)));
        assertPreconditionsMet(mockRequest().evaluatePreconditions(new Date(1000), UNKNOWN_TAG));
    }

    @Test
    public void testIfMatch()
    {
        EntityTag tag = EXPECTED_TAG;
        assertPreconditionsMet(mockRequest().ifMatch(tag).evaluatePreconditions(tag));
        assertPreconditionsFailed(mockRequest().ifMatch(tag).evaluatePreconditions(UNKNOWN_TAG));
    }

    @Test
    public void testIfNoneMatch()
    {
        EntityTag tag = EXPECTED_TAG;
        assertPreconditionsFailed(mockRequest().ifNoneMatch(tag).evaluatePreconditions(tag));
        assertPreconditionsMet(mockRequest().ifNoneMatch(tag).evaluatePreconditions(UNKNOWN_TAG));
    }

    @Test
    public void testIfUnmodifiedSince()
    {
        Date before = new Date(1111);
        Date after = new Date(9999);
        assertPreconditionsFailed(mockRequest().ifUnmodifiedSince(before).evaluatePreconditions(after));
        assertPreconditionsMet(mockRequest().ifUnmodifiedSince(after).evaluatePreconditions(before));
    }

    @Test
    public void testFullPreconditions()
    {
        assertPreconditionsMet(mockRequest().ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsFailed(mockRequest().ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));

        assertPreconditionsFailed(mockRequest().ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsMet(mockRequest().ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));

        assertPreconditionsFailed(mockRequest().ifUnmodifiedSince(BEFORE).evaluatePreconditions(AFTER, UNKNOWN_TAG));
        assertPreconditionsMet(mockRequest().ifUnmodifiedSince(AFTER).evaluatePreconditions(BEFORE, UNKNOWN_TAG));

        assertPreconditionsMet(mockRequest().ifUnmodifiedSince(AFTER).ifMatch(EXPECTED_TAG).ifNoneMatch(UNKNOWN_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsFailed(mockRequest().ifUnmodifiedSince(BEFORE).ifMatch(EXPECTED_TAG).ifNoneMatch(UNKNOWN_TAG).evaluatePreconditions(AFTER, EXPECTED_TAG));
        assertPreconditionsFailed(mockRequest().ifUnmodifiedSince(AFTER).ifMatch(UNKNOWN_TAG).ifNoneMatch(UNKNOWN_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        assertPreconditionsFailed(mockRequest().ifUnmodifiedSince(AFTER).ifMatch(EXPECTED_TAG).ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
    }

    private void assertPreconditionsFailed(ResponseBuilder responseBuilder)
    {
        Assert.assertNotNull(responseBuilder, "Expected a response builder");
        Response response = responseBuilder.build();
        Assert.assertEquals(response.getStatus(), Status.PRECONDITION_FAILED.getStatusCode());
    }

    private void assertPreconditionsMet(ResponseBuilder responseBuilder)
    {
        Assert.assertNull(responseBuilder, "Expected null response builder");
    }
}
