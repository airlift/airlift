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

import com.google.common.base.Preconditions;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;
import java.util.Date;
import java.util.List;

public class MockRequest implements Request
{
    public static MockRequest mockRequest()
    {
        return new MockRequest();
    }

    private String method;
    private EntityTag ifMatch;
    private EntityTag ifNoneMatch;
    private Date ifUnmodifiedSince;
    private Variant selectVariant;

    private MockRequest()
    {
    }

    public MockRequest method(String method)
    {
        Preconditions.checkNotNull(method, "method is null");
        this.method = method;
        return this;
    }

    public MockRequest ifMatch(EntityTag ifMatch)
    {
        Preconditions.checkNotNull(ifMatch, "ifMatch is null");
        Preconditions.checkArgument(!ifMatch.isWeak(), "ifMatch is weak");
        this.ifMatch = ifMatch;
        return this;
    }

    public MockRequest ifNoneMatch(EntityTag ifNoneMatch)
    {
        Preconditions.checkNotNull(ifNoneMatch, "ifNoneMatch is null");
        this.ifNoneMatch = ifNoneMatch;
        return this;
    }

    public MockRequest ifUnmodifiedSince(Date ifUnmodifiedSince)
    {
        Preconditions.checkNotNull(ifUnmodifiedSince, "ifUnmodifiedSince is null");
        this.ifUnmodifiedSince = ifUnmodifiedSince;
        return this;
    }

    public MockRequest setSelectVariant(Variant selectVariant)
    {
        Preconditions.checkNotNull(selectVariant, "selectVariant is null");
        this.selectVariant = selectVariant;
        return this;
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
        Preconditions.checkNotNull(variants, "variants is null");
        Preconditions.checkArgument(!variants.isEmpty(), "variants is empty");

        return selectVariant;
    }

    @Override
    public ResponseBuilder evaluatePreconditions(EntityTag eTag)
    {
        Preconditions.checkNotNull(eTag, "eTag is null");

        if (ifMatch != null && !ifMatch.equals(eTag)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }
        return null;
    }

    @Override
    public ResponseBuilder evaluatePreconditions(Date lastModified)
    {
        Preconditions.checkNotNull(lastModified, "lastModified is null");

        if (this.ifUnmodifiedSince != null && !this.ifUnmodifiedSince.after(lastModified)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }

        return null;
    }

    @Override
    public ResponseBuilder evaluatePreconditions(Date lastModified, EntityTag eTag)
    {
        Preconditions.checkNotNull(eTag, "eTag is null");
        Preconditions.checkNotNull(lastModified, "lastModified is null");

        if (ifMatch != null && !ifMatch.equals(eTag)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }
        if (this.ifUnmodifiedSince != null && !this.ifUnmodifiedSince.after(lastModified)) {
            return Response.status(Response.Status.PRECONDITION_FAILED);
        }

        return null;
    }

    @Override
    public ResponseBuilder evaluatePreconditions()
    {
        if (ifMatch != null) {
            return null;
        }
        return Response.status(Response.Status.PRECONDITION_FAILED);
    }
}
