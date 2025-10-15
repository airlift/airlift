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

import static io.airlift.jaxrs.testing.MockRequest.delete;
import static io.airlift.jaxrs.testing.MockRequest.get;
import static io.airlift.jaxrs.testing.MockRequest.head;
import static io.airlift.jaxrs.testing.MockRequest.post;
import static io.airlift.jaxrs.testing.MockRequest.put;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_XML_TYPE;
import static java.util.Locale.US;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.airlift.jaxrs.testing.MockRequest.ConditionalRequestBuilder;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Variant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class TestMockRequest {
    private record UriBuilder(ConditionalRequestBuilder requestBuilder, String method, Variant variant) {}

    private List<UriBuilder> requestBuildersTestCases() {
        return ImmutableList.of(
                new UriBuilder(head(), "HEAD", null),
                new UriBuilder(get(), "GET", null),
                new UriBuilder(post(), "POST", null),
                new UriBuilder(put(), "PUT", null),
                new UriBuilder(delete(), "DELETE", null),
                new UriBuilder(head(VARIANT), "HEAD", VARIANT),
                new UriBuilder(get(VARIANT), "GET", VARIANT),
                new UriBuilder(post(VARIANT), "POST", VARIANT),
                new UriBuilder(put(VARIANT), "PUT", VARIANT),
                new UriBuilder(delete(VARIANT), "DELETE", VARIANT));
    }

    public static final EntityTag UNKNOWN_TAG = new EntityTag("unknown");
    public static final EntityTag EXPECTED_TAG = new EntityTag("tag");
    public static final Date BEFORE = new Date(1111);
    public static final Date AFTER = new Date(9999);
    public static final Variant VARIANT = new Variant(TEXT_PLAIN_TYPE, Locale.UK, "UTF-8");
    public static final ImmutableList<Variant> VARIANTS = ImmutableList.of(new Variant(TEXT_XML_TYPE, US, "UTF-8"));

    @Test
    public void testMethod() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertThat(testCase.requestBuilder.unconditionally().getMethod()).isEqualTo(testCase.method);
        }
    }

    @Test
    public void testSelectVariant() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertThat(testCase.requestBuilder.unconditionally().selectVariant(VARIANTS))
                    .isEqualTo(testCase.variant);
        }
    }

    @Test
    public void testDefaultPreconditions() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertPreconditionsMet(testCase.requestBuilder.unconditionally().evaluatePreconditions());
            assertPreconditionsMet(testCase.requestBuilder.unconditionally().evaluatePreconditions(BEFORE));
            assertPreconditionsMet(testCase.requestBuilder.unconditionally().evaluatePreconditions(UNKNOWN_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.unconditionally().evaluatePreconditions(BEFORE, UNKNOWN_TAG));
        }
    }

    @Test
    public void testIfMatch() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertPreconditionsFailed(
                    testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions());
            assertPreconditionsMet(testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE));
            assertPreconditionsMet(testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(EXPECTED_TAG));
            assertPreconditionsFailed(
                    testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(UNKNOWN_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
            assertPreconditionsFailed(
                    testCase.requestBuilder.ifMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));
        }
    }

    @Test
    public void testIfNoneMatch() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertPreconditionsMet(
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions());
            assertPreconditionsMet(
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE));
            assertIfNoneMatchFailed(
                    testCase.method,
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(EXPECTED_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(UNKNOWN_TAG));
            assertIfNoneMatchFailed(
                    testCase.method,
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, EXPECTED_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifNoneMatch(EXPECTED_TAG).evaluatePreconditions(BEFORE, UNKNOWN_TAG));
        }
    }

    @Test
    public void testIfModifiedSince() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertPreconditionsMet(
                    testCase.requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions());
            assertIfModifiedSinceFailed(
                    testCase.method,
                    testCase.requestBuilder.ifModifiedSince(AFTER).evaluatePreconditions(BEFORE));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(AFTER));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(EXPECTED_TAG));
            assertIfModifiedSinceFailed(
                    testCase.method,
                    testCase.requestBuilder.ifModifiedSince(AFTER).evaluatePreconditions(BEFORE, EXPECTED_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifModifiedSince(BEFORE).evaluatePreconditions(AFTER, EXPECTED_TAG));
        }
    }

    @Test
    public void testIfUnmodifiedSince() {
        for (UriBuilder testCase : requestBuildersTestCases()) {
            assertPreconditionsMet(
                    testCase.requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions());
            assertPreconditionsFailed(
                    testCase.requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions(AFTER));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(BEFORE));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(EXPECTED_TAG));
            assertPreconditionsFailed(
                    testCase.requestBuilder.ifUnmodifiedSince(BEFORE).evaluatePreconditions(AFTER, EXPECTED_TAG));
            assertPreconditionsMet(
                    testCase.requestBuilder.ifUnmodifiedSince(AFTER).evaluatePreconditions(BEFORE, EXPECTED_TAG));
        }
    }

    private void assertPreconditionsMet(ResponseBuilder responseBuilder) {
        assertThat(responseBuilder).as("Expected null response builder").isNull();
    }

    private void assertPreconditionsFailed(ResponseBuilder responseBuilder) {
        assertThat(responseBuilder).as("Expected a response builder").isNotNull();
        Response response = responseBuilder.build();
        assertThat(response.getStatus()).isEqualTo(Status.PRECONDITION_FAILED.getStatusCode());
    }

    private void assertIfNoneMatchFailed(String method, ResponseBuilder responseBuilder) {
        assertThat(responseBuilder).as("Expected a response builder").isNotNull();
        Response response = responseBuilder.build();

        // not modified only applies to GET and HEAD; otherwise it is a precondition failed
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            assertThat(response.getStatus()).isEqualTo(Status.NOT_MODIFIED.getStatusCode());
        } else {
            assertThat(response.getStatus()).isEqualTo(Status.PRECONDITION_FAILED.getStatusCode());
        }
    }

    private void assertIfModifiedSinceFailed(String method, ResponseBuilder responseBuilder) {
        // if modified since only applies to GET and HEAD; otherwise it process request
        if (!("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))) {
            assertThat(responseBuilder).as("Did NOT expect a response builder").isNull();
        } else {
            assertThat(responseBuilder).as("Expected a response builder").isNotNull();
            Response response = responseBuilder.build();

            assertThat(response.getStatus()).isEqualTo(Status.NOT_MODIFIED.getStatusCode());
        }
    }
}
