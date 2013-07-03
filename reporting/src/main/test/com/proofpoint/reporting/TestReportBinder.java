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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.proofpoint.stats.Gauge;
import com.proofpoint.stats.Reported;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.Flatten;
import org.weakref.jmx.Managed;
import org.weakref.jmx.Nested;
import org.weakref.jmx.guice.MBeanModule;
import org.weakref.jmx.testing.TestingMBeanServer;

import javax.inject.Qualifier;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestReportBinder
{
    private static final String PACKAGE_NAME = "com.proofpoint.reporting";
    private final ObjectName gaugeClassName;
    private final ObjectName annotatedGaugeClassName;
    private final ObjectName reportedClassName;
    private final ObjectName managedClassName;
    private final ObjectName nestedClassName;
    private final ObjectName flattenClassName;
    private MBeanServer jmxMbeanServer;

    public TestReportBinder()
            throws MalformedObjectNameException
    {
        gaugeClassName = ObjectName.getInstance(PACKAGE_NAME, "name", "GaugeClass");
        annotatedGaugeClassName = ObjectName.getInstance(PACKAGE_NAME + ":type=GaugeClass,name=TestingAnnotation");
        reportedClassName = ObjectName.getInstance(PACKAGE_NAME, "name", "ReportedClass");
        managedClassName = ObjectName.getInstance(PACKAGE_NAME, "name", "ManagedClass");
        nestedClassName = ObjectName.getInstance(PACKAGE_NAME, "name", "NestedClass");
        flattenClassName = ObjectName.getInstance(PACKAGE_NAME, "name", "FlattenClass");
    }

    @BeforeMethod
    public void setup()
    {
        jmxMbeanServer = new TestingMBeanServer();
    }

    @Test
    public void testGauge() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(GaugeClass.class).withGeneratedName();
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Gauge", "Reported"), gaugeClassName);
        assertJmxRegistration(ImmutableSet.of("Gauge", "Managed"), gaugeClassName);
    }

    @Test
    public void testGaugeWithAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(GaugeClass.class).annotatedWith(TestingAnnotation.class).withGeneratedName();
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Gauge", "Reported"), annotatedGaugeClassName);
        assertJmxRegistration(ImmutableSet.of("Gauge", "Managed"), annotatedGaugeClassName);
    }

    @Test
    public void testGaugeWithNameAnnotation() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(GaugeClass.class).annotatedWith(Names.named("TestingAnnotation")).withGeneratedName();
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Gauge", "Reported"), annotatedGaugeClassName);
        assertJmxRegistration(ImmutableSet.of("Gauge", "Managed"), annotatedGaugeClassName);
    }

    @Test
    public void testGaugeWithName() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(GaugeClass.class).as(annotatedGaugeClassName.getCanonicalName());
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Gauge", "Reported"), annotatedGaugeClassName);
        assertJmxRegistration(ImmutableSet.of("Gauge", "Managed"), annotatedGaugeClassName);
    }

    @Test
    public void testReportedOnly() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(ReportedClass.class).withGeneratedName();
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Reported"), reportedClassName);
        assertJmxRegistration(ImmutableSet.<String>of(), reportedClassName); // todo make jmxutils not register if empty
    }

    @Test
    public void testManagedOnly() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(ManagedClass.class).withGeneratedName();
                    }
                });
        assertNoReportRegistration(injector);
        assertJmxRegistration(ImmutableSet.of("Managed"), managedClassName);
    }

    @Test
    public void testNested() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(NestedClass.class).withGeneratedName();
                    }
                });
        injector.getInstance(NestedClass.class);
        assertReportRegistration(injector, ImmutableSet.of("Nested.Gauge", "Nested.Reported"), nestedClassName);
        assertJmxRegistration(ImmutableSet.of("Nested.Gauge", "Nested.Managed"), nestedClassName);
    }

    @Test
    public void testFlatten() {
        Injector injector = Guice.createInjector(
                new TestingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        reportBinder(binder).export(FlattenClass.class).withGeneratedName();
                    }
                });
        assertReportRegistration(injector, ImmutableSet.of("Gauge", "Reported"), flattenClassName);
        assertJmxRegistration(ImmutableSet.of("Gauge", "Managed"), flattenClassName);
    }

    private void assertReportRegistration(Injector injector, Set<String> expectedAttribues, ObjectName objectName)
    {
        ReportedBeanRegistry beanServer = injector.getInstance(ReportedBeanRegistry.class);

        Map<ObjectName, ReportedBean> reportedBeans = beanServer.getReportedBeans();
        assertEquals(reportedBeans.keySet(), ImmutableSet.of(
                objectName
        ));

        MBeanInfo mBeanInfo = getOnlyElement(reportedBeans.values()).getMBeanInfo();
        assertAttributes(mBeanInfo, expectedAttribues);
    }

    private void assertNoReportRegistration(Injector injector)
    {
        ReportedBeanRegistry beanServer = injector.getInstance(ReportedBeanRegistry.class);

        Map<ObjectName, ReportedBean> reportedBeans = beanServer.getReportedBeans();
        assertEquals(reportedBeans.keySet(), ImmutableSet.<ObjectName>of());
    }

    private void assertJmxRegistration(Set<String> expectedAttribues, ObjectName objectName)
    {
        MBeanInfo mBeanInfo = null;
        try {
            mBeanInfo = jmxMbeanServer.getMBeanInfo(objectName);
        }
        catch (Exception e) {
            fail("unexpected exception", e);
        }

        assertEquals((int)jmxMbeanServer.getMBeanCount(), 1);
        assertAttributes(mBeanInfo, expectedAttribues);
    }

    private void assertAttributes(MBeanInfo mBeanInfo, Set<String> expected)
    {
        Builder<String> builder = ImmutableSet.builder();
        for (MBeanAttributeInfo mBeanAttributeInfo : mBeanInfo.getAttributes()) {
            String name = mBeanAttributeInfo.getName();
            builder.add(name);
            assertTrue(mBeanAttributeInfo.isReadable(), name + " is readable");
            assertFalse(mBeanAttributeInfo.isWritable(), name + " is writable");
        }
        assertEquals(builder.build(), expected);
    }

    public static class GaugeClass {
        @Gauge
        public String getGauge()
        {
            return "gauge";
        }

        @Reported
        public String getReported()
        {
            return "reported";
        }

        @Managed
        public String getManaged()
        {
            return "managed";
        }
    }

    private static class ReportedClass {
        @Reported
        public String getReported()
        {
            return "reported";
        }
    }

    private static class ManagedClass {
        @Managed
        public String getManaged()
        {
            return "managed";
        }
    }

    public static class NestedClass {
        private final GaugeClass nested = new GaugeClass();

        @Managed
        @Nested
        public GaugeClass getNested()
        {
            return nested;
        }
    }

    public static class FlattenClass {
        private final GaugeClass flatten = new GaugeClass();

        @Managed
        @Flatten
        public GaugeClass getFlatten()
        {
            return flatten;
        }
    }

    private class TestingModule implements Module
    {
        @Override
        public void configure(Binder binder)
        {
            binder.requireExplicitBindings();
            binder.disableCircularProxies();
            binder.bind(MBeanServer.class).toInstance(jmxMbeanServer);
            binder.bind(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(GaugeClass.class).annotatedWith(TestingAnnotation.class).to(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(GaugeClass.class).annotatedWith(Names.named("TestingAnnotation")).to(GaugeClass.class).in(Scopes.SINGLETON);
            binder.bind(ReportedClass.class).in(Scopes.SINGLETON);
            binder.bind(ManagedClass.class).in(Scopes.SINGLETON);
            binder.bind(NestedClass.class).in(Scopes.SINGLETON);
            binder.bind(FlattenClass.class).in(Scopes.SINGLETON);
            binder.install(new MBeanModule());
            binder.install(new ReportingModule());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Qualifier
    private @interface TestingAnnotation
    {}
}
