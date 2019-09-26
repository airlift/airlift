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
package com.facebook.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import org.testng.annotations.Test;

import java.net.URI;

import static com.facebook.airlift.http.client.Request.Builder.prepareGet;
import static com.facebook.airlift.http.client.Request.Builder.preparePut;
import static com.facebook.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.facebook.airlift.testing.EquivalenceTester.equivalenceTester;

public class TestRequest
{
    @Test
    public void testEquivalence()
    {
        BodyGenerator bodyGenerator = createBodyGenerator();

        equivalenceTester()
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setFollowRedirects(true).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setPreserveAuthorizationOnRedirect(false).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setFollowRedirects(false).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setPreserveAuthorizationOnRedirect(true).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(bodyGenerator).build(),
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(bodyGenerator).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersB()).setBodyGenerator(bodyGenerator).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriB()).addHeaders(createHeadersA()).setBodyGenerator(bodyGenerator).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).build(),
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriB()).addHeaders(createHeadersA()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersB()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(bodyGenerator).build(),
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(bodyGenerator).build())
                .addEquivalentGroup(
                        prepareGet().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(createBodyGenerator()).build())
                .addEquivalentGroup(
                        preparePut().setUri(createUriA()).addHeaders(createHeadersA()).setBodyGenerator(createBodyGenerator()).build())
                .check();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Cannot make requests to HTTP port 0")
    public void testCannotMakeRequestToIllegalPort()
    {
        prepareGet().setUri(URI.create("http://example.com:0/")).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri does not have a host: http:///foo")
    public void testInvalidUriMissingHost()
    {
        prepareGet().setUri(URI.create("http:///foo")).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri does not have a scheme: //foo")
    public void testInvalidUriMissingScheme()
    {
        prepareGet().setUri(URI.create("//foo")).build();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "uri scheme must be http or https: gopher://example.com")
    public void testInvalidUriScheme()
    {
        prepareGet().setUri(URI.create("gopher://example.com")).build();
    }

    private static URI createUriA()
    {
        return URI.create("http://example.com");
    }

    private static URI createUriB()
    {
        return URI.create("http://example.net");
    }

    private static ListMultimap<String, String> createHeadersA()
    {
        return ImmutableListMultimap.<String, String>builder()
                .put("foo", "bar")
                .put("abc", "xyz")
                .build();
    }

    private static ListMultimap<String, String> createHeadersB()
    {
        return ImmutableListMultimap.<String, String>builder()
                .put("foo", "bar")
                .put("abc", "xyz")
                .put("qqq", "www")
                .put("foo", "zzz")
                .build();
    }

    public static BodyGenerator createBodyGenerator()
    {
        return createStaticBodyGenerator(new byte[0]);
    }
}
