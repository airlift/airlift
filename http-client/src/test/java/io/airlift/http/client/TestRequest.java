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
package io.airlift.http.client;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import io.airlift.testing.EquivalenceTester;
import org.testng.annotations.Test;

import java.net.URI;

public class TestRequest
{
    @Test
    public void testEquivalence()
    {
        BodyGenerator bodyGenerator = createBodyGenerator();

        EquivalenceTester.<Request>equivalenceTester()
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), null),
                        new Request(createUri1(), "GET", createHeaders1(), null))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), bodyGenerator),
                        new Request(createUri1(), "GET", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders2(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri2(), "GET", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), null),
                        new Request(createUri1(), "PUT", createHeaders1(), null))
                .addEquivalentGroup(
                        new Request(createUri2(), "PUT", createHeaders1(), null))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders2(), null))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), bodyGenerator),
                        new Request(createUri1(), "PUT", createHeaders1(), bodyGenerator))
                .addEquivalentGroup(
                        new Request(createUri1(), "GET", createHeaders1(), createBodyGenerator()))
                .addEquivalentGroup(
                        new Request(createUri1(), "PUT", createHeaders1(), createBodyGenerator()))
                .check();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Cannot make requests to HTTP port 0")
    public void testCannotMakeRequestToIllegalPort()
            throws Exception
    {
        new Request(URI.create("http://example.com:0/"), "GET", createHeaders1(), createBodyGenerator());
    }

    private URI createUri1()
    {
        return URI.create("http://example.com");
    }

    private URI createUri2()
    {
        return URI.create("http://example.net");
    }

    private ListMultimap<String, String> createHeaders1()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz");
    }

    private ListMultimap<String, String> createHeaders2()
    {
        return ImmutableListMultimap.of("foo", "bar", "abc", "xyz", "qqq", "www", "foo", "zzz");
    }

    public static BodyGenerator createBodyGenerator()
    {
        return StaticBodyGenerator.createStaticBodyGenerator(new byte[0]);
    }
}
