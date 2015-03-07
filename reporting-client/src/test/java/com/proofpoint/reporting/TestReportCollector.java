/*
 * Copyright 2013 Proofpoint, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.management.ObjectName;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.reporting.ReportCollector.REPORT_COLLECTOR_OBJECT_NAME;
import static com.proofpoint.testing.Assertions.assertBetweenInclusive;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static java.lang.System.currentTimeMillis;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

public class TestReportCollector
{
    private MinuteBucketIdProvider bucketIdProvider;
    private ReportedBeanRegistry reportedBeanRegistry;
    private ReportClient reportClient;
    private SerialScheduledExecutorService collectorExecutor;
    private SerialScheduledExecutorService clientExecutor;
    private ReportCollector reportCollector;

    @Captor
    ArgumentCaptor<Table<ObjectName, String, Object>> tableCaptor;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        bucketIdProvider = mock(MinuteBucketIdProvider.class);
        reportedBeanRegistry = new ReportedBeanRegistry();
        reportClient = mock(ReportClient.class);
        collectorExecutor = new SerialScheduledExecutorService();
        clientExecutor = spy(new SerialScheduledExecutorService());
        reportCollector = new ReportCollector(bucketIdProvider, reportedBeanRegistry, reportClient, collectorExecutor, clientExecutor);
    }

    @Test
    public void testReportingModule()
    {
        Injector injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new TestingMBeanModule(),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.<String, String>of())),
                new JsonModule(),
                new ReportingModule(),
                new ReportingClientModule());
        injector.getInstance(ReportCollector.class);
    }

    @Test
    public void testReportsStartup()
    {
        ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);

        long lowerBound = currentTimeMillis();
        reportCollector.start();
        long upperBound = currentTimeMillis();

        verify(reportClient).report(longCaptor.capture(), tableCaptor.capture());
        verifyNoMoreInteractions(reportClient);
        // We don't actually care which submit method variant got called, just that it got called on the client executor
        verify(clientExecutor).submit(any(Runnable.class));

        assertBetweenInclusive(longCaptor.getValue(), lowerBound, upperBound);

        Table<ObjectName, String, Object> table = tableCaptor.getValue();
        assertEquals(table.cellSet(), ImmutableTable.<ObjectName, String, Object>builder()
                .put(REPORT_COLLECTOR_OBJECT_NAME, "ServerStart", 1)
                .build()
                .cellSet());
    }

    @Test
    public void testCollection()
            throws Exception
    {
        testReportsStartup();

        collectorExecutor.elapseTime(59, TimeUnit.SECONDS);
        verifyNoMoreInteractions(reportClient);

        when(bucketIdProvider.getLastSystemTimeMillis()).thenReturn(12345L);
        ObjectName objectName = ObjectName.getInstance("com.proofpoint.reporting.test", "name", "TestObject");
        reportedBeanRegistry.register(ReportedBean.forTarget(new Object()
        {
            private int metric = 0;

            @Reported
            public int getMetric()
            {
                return ++metric;
            }
        }), objectName);

        collectorExecutor.elapseTime(1, TimeUnit.SECONDS);

        verify(reportClient).report(eq(12345L), tableCaptor.capture());
        verifyNoMoreInteractions(reportClient);
        // We don't actually care which submit method variant got called, just that it got called on the client executor
        verify(clientExecutor, times(2)).submit(any(Runnable.class));

        Table<ObjectName, String, Object> table = tableCaptor.getValue();
        assertEquals(table.cellSet(), ImmutableTable.<ObjectName, String, Object>builder()
                .put(objectName, "Metric", 1)
                .put(REPORT_COLLECTOR_OBJECT_NAME, "NumMetrics", 1)
                .build()
                .cellSet());

        when(bucketIdProvider.getLastSystemTimeMillis()).thenReturn(67890L);
        collectorExecutor.elapseTime(1, TimeUnit.MINUTES);

        verify(reportClient).report(eq(67890L), tableCaptor.capture());
        verifyNoMoreInteractions(reportClient);

        table = tableCaptor.getValue();
        assertEquals(table.cellSet(), ImmutableTable.<ObjectName, String, Object>builder()
                .put(objectName, "Metric", 2)
                .put(REPORT_COLLECTOR_OBJECT_NAME, "NumMetrics", 1)
                .build()
                .cellSet());
    }

    @Test
    public void testUnreportedValues()
            throws Exception
    {
        testReportsStartup();

        when(bucketIdProvider.getLastSystemTimeMillis()).thenReturn(12345L);
        ObjectName objectName = ObjectName.getInstance("com.proofpoint.reporting.test", "name", "TestObject");
        reportedBeanRegistry.register(ReportedBean.forTarget(new Object()
        {
            @Reported
            public double getDoubleMetric()
            {
                return 0;
            }

            @Reported
            public double getNanDouble()
            {
                return Double.NaN;
            }

            @Reported
            public double getInfiniteDouble()
            {
                return Double.NEGATIVE_INFINITY;
            }

            @Reported
            public float getFloatMetric()
            {
                return 0F;
            }

            @Reported
            public float getNanFloat()
            {
                return Float.NaN;
            }

            @Reported
            public float getInfiniteFloat()
            {
                return Float.POSITIVE_INFINITY;
            }

            @Reported
            public long getLongMetric()
            {
                return 0L;
            }

            @Reported
            public long getMaxLongMetric()
            {
                return Long.MAX_VALUE;
            }

            @Reported
            public long getMinLongMetric()
            {
                return Long.MIN_VALUE;
            }

            @Reported
            public int getIntegerMetric()
            {
                return 0;
            }

            @Reported
            public int getMaxIntegerMetric()
            {
                return Integer.MAX_VALUE;
            }

            @Reported
            public int getMinIntegerMetric()
            {
                return Integer.MIN_VALUE;
            }

            @Reported
            public short getShortMetric()
            {
                return 0;
            }

            @Reported
            public short getMaxShortMetric()
            {
                return Short.MAX_VALUE;
            }

            @Reported
            public short getMinShortMetric()
            {
                return Short.MIN_VALUE;
            }

            @Reported
            public byte getByteMetric()
            {
                return 0;
            }

            @Reported
            public byte getMaxByteMetric()
            {
                return Byte.MAX_VALUE;
            }

            @Reported
            public byte getMinByteMetric()
            {
                return Byte.MIN_VALUE;
            }

            @Reported
            public boolean getFalseBooleanMetric()
            {
                return false;
            }

            @Reported
            public Boolean getTrueBooleanMetric()
            {
                return true;
            }

            @Reported
            public Boolean getNullBooleanMetric()
            {
                return null;
            }

            @Reported
            public TestingValue getTestingValueMetric()
            {
                return new TestingValue();
            }

            @Reported
            public Integer getNullMetric()
            {
                return null;
            }

            @Reported
            public int getExceptionMetric()
            {
                throw new UnsupportedOperationException();
            }
        }), objectName);

        collectorExecutor.elapseTime(1, TimeUnit.MINUTES);

        verify(reportClient).report(eq(12345L), tableCaptor.capture());
        verifyNoMoreInteractions(reportClient);

        Table<ObjectName, String, Object> table = tableCaptor.getValue();
        assertEqualsIgnoreOrder(table.cellSet(), ImmutableTable.<ObjectName, String, Object>builder()
                .put(objectName, "DoubleMetric", 0.0)
                .put(objectName, "FloatMetric", 0F)
                .put(objectName, "LongMetric", 0L)
                .put(objectName, "IntegerMetric", 0)
                .put(objectName, "ShortMetric", (short) 0)
                .put(objectName, "ByteMetric", (byte) 0)
                .put(objectName, "MaxByteMetric", Byte.MAX_VALUE)
                .put(objectName, "MinByteMetric", Byte.MIN_VALUE)
                .put(objectName, "FalseBooleanMetric", 0)
                .put(objectName, "TrueBooleanMetric", 1)
                .put(objectName, "TestingValueMetric", "testing toString value")
                .put(REPORT_COLLECTOR_OBJECT_NAME, "NumMetrics", 11)
                .build()
                .cellSet());
    }

    private static class TestingValue
    {
        @Override
        public String toString()
        {
            return "testing toString value";
        }
    }
}
