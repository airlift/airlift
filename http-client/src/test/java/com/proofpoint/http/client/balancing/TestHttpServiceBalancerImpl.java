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
import com.proofpoint.http.client.balancing.HttpServiceBalancerStats.Status;
import com.proofpoint.stats.CounterStat;
import com.proofpoint.stats.TimeStat;
import com.proofpoint.testing.TestingTicker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class TestHttpServiceBalancerImpl
{
    private HttpServiceBalancerImpl httpServiceBalancer;
    private HttpServiceBalancerStats httpServiceBalancerStats;
    private TestingTicker testingTicker;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        httpServiceBalancerStats = mock(HttpServiceBalancerStats.class);
        testingTicker = new TestingTicker();
        httpServiceBalancer = new HttpServiceBalancerImpl("type=[apple], pool=[pool]", httpServiceBalancerStats, testingTicker);
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
        TimeStat failureTimeStat = mock(TimeStat.class);
        when(httpServiceBalancerStats.responseTime(any(URI.class), eq(Status.FAILURE))).thenReturn(failureTimeStat);
        TimeStat successTimeStat = mock(TimeStat.class);
        when(httpServiceBalancerStats.responseTime(any(URI.class), eq(Status.SUCCESS))).thenReturn(successTimeStat);
        CounterStat counterStat = mock(CounterStat.class);
        when(httpServiceBalancerStats.failure(any(URI.class), eq("testing failure"))).thenReturn(counterStat);

        httpServiceBalancer.updateHttpUris(expected);

        Set<URI> uris = new HashSet<>();
        testingTicker.increment(3000, TimeUnit.SECONDS);
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        testingTicker.increment(5, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.increment(5, TimeUnit.SECONDS);
        attempt.markBad("testing failure");
        testingTicker.increment(5, TimeUnit.SECONDS);
        attempt = attempt.next();
        testingTicker.increment(5, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.increment(5, TimeUnit.SECONDS);
        attempt.markBad("testing failure");
        testingTicker.increment(10, TimeUnit.SECONDS);
        attempt = attempt.next();
        testingTicker.increment(10, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.increment(10, TimeUnit.SECONDS);
        attempt.markGood();

        assertEquals(uris, expected);
        for (URI uri : expected) {
            verify(httpServiceBalancerStats).responseTime(uri, Status.FAILURE);
            verify(httpServiceBalancerStats).failure(uri, "testing failure");
        }
        verify(httpServiceBalancerStats).responseTime(any(URI.class), eq(Status.SUCCESS));
        verify(failureTimeStat, times(2)).add(TimeUnit.SECONDS.toNanos(10), TimeUnit.NANOSECONDS);
        verify(successTimeStat).add(TimeUnit.SECONDS.toNanos(20), TimeUnit.NANOSECONDS);
        verify(counterStat, times(2)).update(1);
        verifyNoMoreInteractions(httpServiceBalancerStats, failureTimeStat, successTimeStat, counterStat);
    }
}
