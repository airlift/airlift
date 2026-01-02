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

import org.junit.jupiter.api.Test;

import static io.airlift.testing.EquivalenceTester.equivalenceTester;
import static org.assertj.core.api.Assertions.assertThat;

public class TestServiceTypes
{
    @ServiceType("apple")
    private final ServiceType appleServiceType;

    @ServiceType("banana")
    private final ServiceType bananaServiceType;

    @SuppressWarnings("UnusedVariable") // used by reflection
    @ServiceType("quot\"ation-and-\\backslash")
    private final ServiceType serviceTypeWithCharacters;

    public TestServiceTypes()
    {
        try {
            this.appleServiceType = getClass().getDeclaredField("appleServiceType").getAnnotation(ServiceType.class);
            this.bananaServiceType = getClass().getDeclaredField("bananaServiceType").getAnnotation(ServiceType.class);
            this.serviceTypeWithCharacters = getClass().getDeclaredField("serviceTypeWithCharacters").getAnnotation(ServiceType.class);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testValue()
    {
        assertThat(ServiceTypes.serviceType("type").value()).isEqualTo("type");
    }

    @Test
    public void testAnnotationType()
    {
        assertThat(ServiceTypes.serviceType("apple").annotationType()).isEqualTo(ServiceType.class);
        assertThat(ServiceTypes.serviceType("apple").annotationType()).isEqualTo(appleServiceType.annotationType());
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
