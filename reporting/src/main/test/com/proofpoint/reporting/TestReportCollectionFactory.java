/*
 * Copyright 2014 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.google.common.base.Ticker;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.ObjectNameBuilder;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

public class TestReportCollectionFactory
{
    private ReportExporter reportExporter;
    private TestTicker ticker;
    private ReportCollectionFactory reportCollectionFactory;

    @BeforeMethod
    public void setup()
    {
        reportExporter = mock(ReportExporter.class);
        ticker = new TestTicker();
        reportCollectionFactory = new ReportCollectionFactory(reportExporter, ticker);
    }

    @Test
    public void testKeyedDistribution()
            throws Exception
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class);
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(stringCaptor.capture(), reportCaptor.capture());
        assertEquals(stringCaptor.getValue(), "com.proofpoint.reporting:type=KeyedDistribution,name=Add,foo=value,bar=false");
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test
    public void testNullValue()
            throws Exception
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class);
        SomeObject someObject = keyedDistribution.add(null, false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(stringCaptor.capture(), reportCaptor.capture());
        assertEquals(stringCaptor.getValue(), "com.proofpoint.reporting:type=KeyedDistribution,name=Add,bar=false");
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test
    public void testExpiration()
            throws Exception
    {
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class);
        keyedDistribution.add("value", false);
        ticker.advance(5, TimeUnit.MINUTES);
        keyedDistribution.add("value", false);
        ticker.advance(14, TimeUnit.MINUTES);
        ticker.advance(59, TimeUnit.SECONDS);
        keyedDistribution.add("value", true);

        verify(reportExporter, never()).unexport(any(String.class));

        ticker.advance(1, TimeUnit.SECONDS);
        keyedDistribution.add("value2", true);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

        verify(reportExporter).unexport(stringCaptor.capture());
        assertEquals(stringCaptor.getValue(), "com.proofpoint.reporting:type=KeyedDistribution,name=Add,foo=value,bar=false");
    }

    private interface KeyedDistribution
    {
        SomeObject add(@Key("foo") String key, @NotNull @Key("bar") boolean bool);
    }

    @Test
    public void testNamedCollection()
            throws Exception
    {
        String name = new ObjectNameBuilder(KeyedDistribution.class.getPackage().getName())
                .withProperty("a", "fooval")
                .withProperty("b", "with\"quote")
                .withProperty("c", "with,comma")
                .withProperty("d", "with\\backslash")
                .build();
        KeyedDistribution keyedDistribution = reportCollectionFactory.createReportCollection(KeyedDistribution.class, name);
        SomeObject someObject = keyedDistribution.add("value", false);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SomeObject> reportCaptor = ArgumentCaptor.forClass(SomeObject.class);

        verify(reportExporter).export(stringCaptor.capture(), reportCaptor.capture());
        assertEquals(stringCaptor.getValue(), "com.proofpoint.reporting:a=fooval,b=\"with\\\"quote\",c=\"with,comma\",d=with\\backslash,name=Add,foo=value,bar=false");
        assertSame(reportCaptor.getValue(), someObject);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting\\.TestReportCollectionFactory\\$MissingParameterName\\.add\\(java\\.lang\\.String, boolean\\) parameter 2 has no @com.proofpoint.reporting.Key annotation")
    public void testNoParameterNames()
    {
        reportCollectionFactory.createReportCollection(MissingParameterName.class);
    }

    private interface MissingParameterName
    {
        SomeObject add(@Key("foo") String key, boolean bool);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting\\.TestReportCollectionFactory\\$NotConstructable\\.add\\(java\\.lang\\.String, boolean\\) return type ConstructorNeedsArgument has no public no-arg constructor")
    public void testReturnTypeNotConstructable()
    {
        reportCollectionFactory.createReportCollection(NotConstructable.class);
    }

    private interface NotConstructable
    {
        ConstructorNeedsArgument add(@Key("foo") String key, @Key("bar") boolean bool);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting\\.TestReportCollectionFactory\\$MissingParameters\\.add\\(\\) has no parameters")
    public void testNoParameters()
    {
        reportCollectionFactory.createReportCollection(MissingParameters.class);
    }

    private interface MissingParameters
    {
        SomeObject add();
    }

    public static class SomeObject
    {
    }

    public static class ConstructorNeedsArgument
    {
        public ConstructorNeedsArgument(int something)
        {
        }
    }

    private static class TestTicker extends Ticker
    {
        private long nanos = 0;

        @Override
        public long read()
        {
            return nanos;
        }

        public void advance(int amount, TimeUnit unit)
        {
            nanos += unit.toNanos(amount);
        }
    }
}
