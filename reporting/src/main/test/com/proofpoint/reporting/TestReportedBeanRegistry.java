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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import static org.testng.Assert.assertEquals;

public class TestReportedBeanRegistry
{
    public static final ReportedBean TESTING_REPORTED_BEAN = ReportedBean.forTarget(new Object()
    {
        @Gauge
        public int getMetric()
        {
            return 1;
        }
    });

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

    @BeforeMethod
    public void setup()
    {
        registry = new ReportedBeanRegistry();
    }

    @Test
    public void testRegister()
            throws Exception
    {
        registry.register(TESTING_REPORTED_BEAN, TESTING_OBJECT_NAME);
        assertEquals(registry.getReportedBeans(), ImmutableMap.of(TESTING_OBJECT_NAME, TESTING_REPORTED_BEAN));
    }

    @Test
    public void testUnRegister()
            throws Exception
    {
        registry.register(TESTING_REPORTED_BEAN, TESTING_OBJECT_NAME);
        registry.unregister(TESTING_OBJECT_NAME);
        assertEquals(registry.getReportedBeans(), ImmutableMap.of());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testRegisterNullName()
            throws Exception
    {
        registry.register(TESTING_REPORTED_BEAN, null);
    }

    @Test(expectedExceptions = InstanceAlreadyExistsException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting:name=TestingObject is already registered")
    public void testRegisterTwice()
            throws Exception
    {
        registry.register(TESTING_REPORTED_BEAN, TESTING_OBJECT_NAME);
        registry.register(TESTING_REPORTED_BEAN, TESTING_OBJECT_NAME);
    }

    @Test(expectedExceptions = InstanceNotFoundException.class,
            expectedExceptionsMessageRegExp = "com\\.proofpoint\\.reporting:name=TestingObject not found")
    public void testUnRegisterNotFound()
            throws Exception
    {
        registry.unregister(TESTING_OBJECT_NAME);
    }
}
