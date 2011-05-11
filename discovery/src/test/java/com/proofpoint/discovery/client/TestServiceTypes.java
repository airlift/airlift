package com.proofpoint.discovery.client;

import com.google.common.base.Throwables;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.proofpoint.testing.EquivalenceTester.equivalenceTester;

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
