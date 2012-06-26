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
package io.airlift.discovery.client;

import com.google.common.base.Throwables;
import org.testng.Assert;
import org.testng.annotations.Test;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;

public class TestServiceTypes
{
    @ServiceType("apple")
    private final ServiceType appleServiceType;

    @ServiceType("banana")
    private final ServiceType bananaServiceType;

    public TestServiceTypes()
    {
        try {
            this.appleServiceType = getClass().getDeclaredField("appleServiceType").getAnnotation(ServiceType.class);
            this.bananaServiceType = getClass().getDeclaredField("bananaServiceType").getAnnotation(ServiceType.class);
        }
        catch (NoSuchFieldException e) {
            throw Throwables.propagate(e);
        }
    }

    @Test
    public void testValue()
    {
        Assert.assertEquals(ServiceTypes.serviceType("type").value(), "type");
    }


    @Test
    public void testToString()
    {
        Assert.assertEquals(ServiceTypes.serviceType("apple").toString(), appleServiceType.toString());
    }

    @Test
    public void testAnnotationType()
    {
        Assert.assertEquals(ServiceTypes.serviceType("apple").annotationType(), ServiceType.class);
        Assert.assertEquals(ServiceTypes.serviceType("apple").annotationType(), appleServiceType.annotationType());
    }

    @Test
    public void testEquivalence()
    {
        equivalenceTester()
                .addEquivalentGroup(appleServiceType, ServiceTypes.serviceType("apple"))
                .addEquivalentGroup(bananaServiceType, ServiceTypes.serviceType("banana"))
                .check();
    }
}
