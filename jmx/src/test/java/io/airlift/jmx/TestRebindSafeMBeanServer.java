package io.airlift.jmx;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.jmx.testing.TestingJmxModule;
import org.testng.annotations.Test;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.weakref.jmx.ObjectNames.generatedNameOf;

public class TestRebindSafeMBeanServer
{
    @Test
    public void testRebindSafeMBeanServer()
            throws Exception
    {
        Injector injector = Guice.createInjector(new TestingJmxModule());
        MBeanServer mbeanServer = injector.getInstance(MBeanServer.class);
        RebindSafeMBeanServer rebindSafeMBeanServer = new RebindSafeMBeanServer(mbeanServer);
        TestMBean testMBean = new TestMBean();
        ObjectName name = new ObjectName(generatedNameOf(TestMBean.class));
        rebindSafeMBeanServer.registerMBean(testMBean, name);
        assertTrue(rebindSafeMBeanServer.isRegistered(name));
        ObjectInstance existing = rebindSafeMBeanServer.registerMBean(testMBean, name);
        assertEquals(existing.getObjectName(), name);
        rebindSafeMBeanServer.unregisterMBean(name);
        assertFalse(rebindSafeMBeanServer.isRegistered(name));
    }

    class TestMBean
            implements DynamicMBean
    {
        @Override
        public Object getAttribute(String attribute)
                throws AttributeNotFoundException, MBeanException, ReflectionException
        {
            return null;
        }

        @Override
        public void setAttribute(Attribute attribute)
                throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException
        {
        }

        @Override
        public AttributeList getAttributes(String[] attributes)
        {
            return null;
        }

        @Override
        public AttributeList setAttributes(AttributeList attributes)
        {
            return null;
        }

        @Override
        public Object invoke(String actionName, Object[] params, String[] signature)
                throws MBeanException, ReflectionException
        {
            return null;
        }

        @Override
        public MBeanInfo getMBeanInfo()
        {
            return new MBeanInfo(generatedNameOf(TestMBean.class), "test-mbean", null, null, null, null);
        }
    }
}
