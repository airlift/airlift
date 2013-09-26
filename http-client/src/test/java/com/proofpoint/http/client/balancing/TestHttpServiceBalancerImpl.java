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
package com.proofpoint.http.client.balancing;

import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;

public class TestHttpServiceBalancerImpl
{
    private HttpServiceBalancerImpl httpServiceBalancer;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        httpServiceBalancer = new HttpServiceBalancerImpl("type=[apple], pool=[pool]");
    }

    @Test(expectedExceptions = ServiceUnavailableException.class)
    public void testNotStartedEmpty()
    {
        httpServiceBalancer.createAttempt();
    }

    @Test(expectedExceptions = ServiceUnavailableException.class)
    public void testStartedEmpty()
            throws Exception
    {
        httpServiceBalancer.updateHttpUris(ImmutableSet.<URI>of());

        httpServiceBalancer.createAttempt();
    }

    @Test
    public void testStartedWithServices()
            throws Exception
    {
        ImmutableSet<URI> expected = ImmutableSet.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-a.example.com"));

        httpServiceBalancer.updateHttpUris(expected);

        Set<URI> uris = new HashSet<>();
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        uris.add(attempt.getUri());
        attempt.markBad("testing failure");
        attempt = attempt.next();
        uris.add(attempt.getUri());
        attempt.markBad("testing failure");
        attempt = attempt.next();
        uris.add(attempt.getUri());

        assertEquals(uris, expected);
    }
}
