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
package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.reporting.ReportException.Reason;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Nested;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestReportExporter
{
    private static final Object TESTING_OBJECT = new Object()
    {
        @Gauge
        public int getMetric()
        {
            return 1;
        }
    };

    private static final ObjectName TESTING_OBJECT_NAME;

    static {
        ObjectName objectName = null;
        try {
            objectName = ObjectName.getInstance("com.proofpoint.reporting", "name", "TestingObject");
        }
        catch (MalformedObjectNameException ignored) {
        }
        TESTING_OBJECT_NAME = objectName;
    }

    private ReportedBeanRegistry registry;
    @Mock
    private BucketIdProvider bucketIdProvider;
    @Mock
    private MBeanExporter mBeanExporter;
    private ReportExporter reportExporter;

    @BeforeMethod
    public void setup()
    {
        initMocks(this);
        registry = new ReportedBeanRegistry();
        reportExporter = new ReportExporter(registry, bucketIdProvider, mBeanExporter);
    }

    @Test
    public void testExportString()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
        assertExported();
    }

    @Test
    public void testExportStringNoAttributes()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), new Object());
        assertEquals(registry.getReportedBeans(), ImmutableMap.of());
    }

    @Test
    public void testExportStringMalformedName()
            throws Exception
    {
        try {
            reportExporter.export("TestingObject", TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.MALFORMED_OBJECT_NAME);
            assertEquals(e.getMessage(), "Key properties cannot be empty");
        }
    }

    @Test
    public void testExportStringDuplicate()
            throws Exception
    {
        try {
            reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
            reportExporter.export(TESTING_OBJECT_NAME.getCanonicalName(), TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_ALREADY_EXISTS);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject is already registered");
        }
    }

    @Test
    public void testExportObjectName()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        assertExported();
    }

    @Test
    public void testExportObjectNameNoAttributes()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, new Object());
        assertEquals(registry.getReportedBeans(), ImmutableMap.of());
    }

    @Test
    public void testExportObjectNameDuplicate()
            throws Exception
    {
        try {
            reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
            reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_ALREADY_EXISTS);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject is already registered");
        }
    }

    @Test
    public void testUnexportString()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        reportExporter.unexport(TESTING_OBJECT_NAME.getCanonicalName());
        assertEquals(registry.getReportedBeans(), ImmutableMap.of());
    }

    @Test
    public void testUnexportStringMalformedName()
            throws Exception
    {
        try {
            reportExporter.unexport("TestingObject");
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.MALFORMED_OBJECT_NAME);
            assertEquals(e.getMessage(), "Key properties cannot be empty");
        }
    }

    @Test
    public void testUnexportStringNotRegistered()
            throws Exception
    {
        try {
            reportExporter.unexport(TESTING_OBJECT_NAME.getCanonicalName());
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject not found");
        }
    }

    @Test
    public void testUnexportObjectName()
            throws Exception
    {
        reportExporter.export(TESTING_OBJECT_NAME, TESTING_OBJECT);
        reportExporter.unexport(TESTING_OBJECT_NAME);
        assertEquals(registry.getReportedBeans(), ImmutableMap.of());
    }

    @Test
    public void testUnexportObjectNameNotRegistered()
            throws Exception
    {
        try {
            reportExporter.unexport(TESTING_OBJECT_NAME);
            fail("expected ReportException");
        }
        catch (ReportException e) {
            assertEquals(e.getReason(), Reason.INSTANCE_NOT_FOUND);
            assertEquals(e.getMessage(), "com.proofpoint.reporting:name=TestingObject not found");
        }
    }

    @Test
    public void testNotifyBucketIdProvider()
    {
        TestingBucketed bucketed = spy(new TestingBucketed());
        reportExporter.export(TESTING_OBJECT_NAME, bucketed);

        verify(bucketed).setBucketIdProvider(bucketIdProvider);
        verify(bucketed.getInnerBucketed()).setBucketIdProvider(bucketIdProvider);
    }

    private void assertExported()
    {
        assertEquals(registry.getReportedBeans().keySet(), ImmutableSet.of(TESTING_OBJECT_NAME));
        assertEquals(registry.getReportedBeans().get(TESTING_OBJECT_NAME).getMBeanInfo(), ReportedBean.forTarget(TESTING_OBJECT).getMBeanInfo());

        verify(mBeanExporter).export(TESTING_OBJECT_NAME, TESTING_OBJECT);
    }

    private static class TestingBucketed
        extends Bucketed<Object>
    {
        private InnerBucketed innerBucketed = spy(new InnerBucketed());

        @Override
        protected Object createBucket()
        {
            return new Object();
        }

        @Nested
        public InnerBucketed getInnerBucketed() {
            return innerBucketed;
        }
    }

    private static class InnerBucketed
        extends Bucketed<Object>
    {
        @Override
        protected Object createBucket()
        {
            return new Object();
        }
    }
}
