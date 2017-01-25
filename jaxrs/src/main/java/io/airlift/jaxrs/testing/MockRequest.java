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

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;

import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public class MockRequest implements Request
{
    public static ConditionalRequestBuilder head()
    {
        return new ConditionalRequestBuilder("HEAD");
    }

    public static ConditionalRequestBuilder head(Variant selectVariant)
    {
        return new ConditionalRequestBuilder("HEAD", selectVariant);
    }

    public static ConditionalRequestBuilder get()
    {
        return new ConditionalRequestBuilder("GET");
    }

    public static ConditionalRequestBuilder get(Variant selectVariant)
    {
        return new ConditionalRequestBuilder("GET", selectVariant);
    }

    public static ConditionalRequestBuilder post()
    {
        return new ConditionalRequestBuilder("POST");
    }

    public static ConditionalRequestBuilder post(Variant selectVariant)
    {
        return new ConditionalRequestBuilder("POST", selectVariant);
    }

    public static ConditionalRequestBuilder put()
    {
        return new ConditionalRequestBuilder("PUT");
    }

    public static ConditionalRequestBuilder put(Variant selectVariant)
    {
        return new ConditionalRequestBuilder("PUT", selectVariant);
    }

    public static ConditionalRequestBuilder delete()
    {
        return new ConditionalRequestBuilder("DELETE");
    }

    public static ConditionalRequestBuilder delete(Variant selectVariant)
    {
        return new ConditionalRequestBuilder("DELETE", selectVariant);
    }


    public static class ConditionalRequestBuilder
    {

        private final String method;
        private final Variant selectVariant;

        private ConditionalRequestBuilder(String method)
        {
            this.method = method;
            this.selectVariant = null;
        }

        private ConditionalRequestBuilder(String method, Variant selectVariant)
        {
            this.method = method;
            this.selectVariant = selectVariant;
        }

        public MockRequest ifMatch(EntityTag ifMatch)
        {
            return new MockRequest(method, selectVariant, ifMatch, null, null, null);
        }

        public MockRequest ifNoneMatch(EntityTag ifNoneMatch)
        {
            return new MockRequest(method, selectVariant, null, ifNoneMatch, null, null);
        }

        public MockRequest ifModifiedSince(Date ifModifiedSince)
        {
            return new MockRequest(method, selectVariant, null, null, ifModifiedSince, null);
        }

        public MockRequest ifUnmodifiedSince(Date ifUnmodifiedSince)
        {
            return new MockRequest(method, selectVariant, null, null, null, ifUnmodifiedSince);
        }

        public MockRequest unconditionally()
        {
            return new MockRequest(method, selectVariant, null, null, null, null);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append(method);
            if (selectVariant != null) {
                sb.append("{").append(selectVariant).append('}');
            }
            return sb.toString();
        }
    }

    private final String method;
    private final Variant selectVariant;
    private final EntityTag ifMatch;
    private final EntityTag ifNoneMatch;
    private final Date ifModifiedSince;
    private final Date ifUnmodifiedSince;

    private MockRequest(String method, Variant selectVariant, EntityTag ifMatch, EntityTag ifNoneMatch, Date ifModifiedSince, Date ifUnmodifiedSince)
    {
        this.method = method;
        this.selectVariant = selectVariant;
        this.ifMatch = ifMatch;
        this.ifNoneMatch = ifNoneMatch;
        this.ifModifiedSince = ifModifiedSince;
        this.ifUnmodifiedSince = ifUnmodifiedSince;
    }

    @Override
    public String getMethod()
    {
        return method;
    }

    @Override
    public Variant selectVariant(List<Variant> variants)
            throws IllegalArgumentException
    {
        requireNonNull(variants, "variants is null");
        Preconditions.checkArgument(!variants.isEmpty(), "variants is empty");

        return selectVariant;
    }

    // a call into this method is an indicator that the resource does not exist
    // see C007
    // http://jcp.org/aboutJava/communityprocess/maintenance/jsr311/311ChangeLog.html
    @Override
    public ResponseBuilder evaluatePreconditions()
    {
        // the resource does not exist yet so any If-Match header would result
        // in a precondition failed
        if (ifMatch != null) {
            // we won't find a match. To be consistent with evaluateIfMatch, we
            // return a built response
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }

        // since the resource does not exist yet if there is a If-None-Match
        // header, then this should return null. if there is no If-None-Match
        // header, this should still return null
        return null;
    }

    @Override
    public ResponseBuilder evaluatePreconditions(EntityTag eTag)
    {
        requireNonNull(eTag, "eTag is null");

        return firstNonNull(evaluateIfMatch(eTag), evaluateIfNoneMatch(eTag));
    }

    @Override
    public ResponseBuilder evaluatePreconditions(Date lastModified)
    {
        requireNonNull(lastModified, "lastModified is null");

        return firstNonNull(evaluateIfModifiedSince(lastModified), evaluateIfUnmodifiedSince(lastModified));
    }

    @Override
    public ResponseBuilder evaluatePreconditions(Date lastModified, EntityTag eTag)
    {
        requireNonNull(eTag, "eTag is null");
        requireNonNull(lastModified, "lastModified is null");

        return firstNonNull(evaluatePreconditions(lastModified), evaluatePreconditions(eTag));
    }

    private ResponseBuilder evaluateIfMatch(EntityTag eTag)
    {
        // if request ifMatch is not set, process the request
        if (ifMatch == null) {
            return null;
        }

        // if-match is not allowed with weak eTags
        if (eTag.isWeak()) {
            return Response.status(Response.Status.PRECONDITION_FAILED).tag(eTag);
        }

        // if the request ifMatch eTag matches the supplied eTag, process the request
        if ("*".equals(ifMatch.getValue()) || eTag.getValue().equals(ifMatch.getValue())) {
            return null;
        }

        return Response.status(Response.Status.PRECONDITION_FAILED).tag(eTag);
    }

    private ResponseBuilder evaluateIfNoneMatch(EntityTag tag)
    {
        // if request ifNoneMatch is not set, process the request
        if (ifNoneMatch == null) {
            return null;
        }

        // if the request ifNoneMatch eTag does NOT match the supplied eTag, process the request
        if (!("*".equals(ifNoneMatch.getValue()) || tag.getValue().equals(ifNoneMatch.getValue()))) {
            return null;
        }

        // if this is a GET or HEAD, return not modified otherwise return precondition failed
        if ("GET".equalsIgnoreCase(getMethod()) || "HEAD".equalsIgnoreCase(getMethod())) {
            return Response.notModified(tag);
        }
        else {
            return Response.status(Response.Status.PRECONDITION_FAILED).tag(tag);
        }
    }

    private ResponseBuilder evaluateIfModifiedSince(Date lastModified)
    {
        // if request ifModifiedSince is not set, process the request
        if (ifModifiedSince == null) {
            return null;
        }
        // if modified since only applies to GET and HEAD; otherwise it process request
        if (!("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))) {
            return null;
        }

        // if the request ifModifiedSince is after last modified, process the request
        if (lastModified.after(ifModifiedSince)) {
            return null;
        }

        return Response.notModified();
    }

    private ResponseBuilder evaluateIfUnmodifiedSince(Date lastModified)
    {
        // if request ifUnmodifiedSince is not set, process the request
        if (ifUnmodifiedSince == null) {
            return null;
        }

        // if the request ifUnmodifiedSince is NOT after last modified, process the request
        if (!lastModified.after(ifUnmodifiedSince)) {
            return null;
        }

        return Response.status(Response.Status.PRECONDITION_FAILED);
    }

    private static <T> T firstNonNull(T... objects)
    {
        return Iterables.find(asList(objects), Predicates.<Object>notNull(), null);
    }
}
