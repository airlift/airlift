/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.http.client;

import com.google.common.annotations.Beta;

import java.net.URI;

import static com.proofpoint.http.client.HttpUriBuilder.uriBuilderFrom;

/**
 * An RFC-3986-compatible HTTP URI builder for path-only relative URIs
 */
@Beta
public class RelativeUriBuilder
{
    private static final URI STARTING_URI = URI.create("http://invalid.invalid");
    private static final int STARTING_URI_LENGTH = STARTING_URI.toString().length();

    private HttpUriBuilder delegate;

    private RelativeUriBuilder()
    {
        delegate = uriBuilderFrom(STARTING_URI);
    }

    private RelativeUriBuilder(URI previous)
    {
        delegate = uriBuilderFrom(previous).scheme("http").host("invalid.invalid").defaultPort();
    }

    public static RelativeUriBuilder relativeUriBuilder()
    {
        return new RelativeUriBuilder();
    }

    public static RelativeUriBuilder relativeUriBuilder(String path)
    {
        return relativeUriBuilder().replacePath(path);
    }

    public static RelativeUriBuilder relativeUriBuilderFrom(URI uri)
    {
        return new RelativeUriBuilder(uri);
    }

    /**
     * Replace the current path with the given unencoded path
     */
    public RelativeUriBuilder replacePath(String path)
    {
        delegate = delegate.replacePath(path);
        return this;
    }

    /**
     * Append an unencoded path.
     *
     * All reserved characters except '/' will be percent-encoded. '/' are considered as path separators and
     * appended verbatim.
     */
    public RelativeUriBuilder appendPath(String path)
    {
        delegate = delegate.appendPath(path);
        return this;
    }

    public RelativeUriBuilder replaceParameter(String name, String... values)
    {
        delegate = delegate.replaceParameter(name, values);
        return this;
    }

    public RelativeUriBuilder replaceParameter(String name, Iterable<String> values)
    {
        delegate = delegate.replaceParameter(name, values);
        return this;
    }

    public RelativeUriBuilder addParameter(String name, String... values)
    {
        delegate = delegate.addParameter(name, values);
        return this;
    }

    public RelativeUriBuilder addParameter(String name, Iterable<String> values)
    {
        delegate = delegate.addParameter(name, values);
        return this;
    }

    public RelativeUriBuilder replaceRawQuery(String rawQuery)
    {
        delegate = delegate.replaceRawQuery(rawQuery);
        return this;
    }

    // return an RFC-3986-compatible URI
    public String toString()
    {
        String s = delegate.toString().substring(STARTING_URI_LENGTH);
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    public URI build()
    {
        return STARTING_URI.relativize(delegate.build());
    }
}
