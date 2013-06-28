package com.proofpoint.reporting;

import com.proofpoint.stats.Reported;
import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import java.util.ArrayList;

public class TestReportedBean extends AbstractReportedBeanTest<Object>
{
    public TestReportedBean()
    {
        objects = new ArrayList<>();
        objects.add(new SimpleObject());
        objects.add(new CustomAnnotationObject());
        objects.add(new FlattenObject());
        objects.add(new CustomFlattenAnnotationObject());
        objects.add(new NestedObject());
        objects.add(new CustomNestedAnnotationObject());
    }

    @Override
    protected Object getObject(Object o) {
        return o;
    }

    @Override
    protected MBeanInfo getMBeanInfo(Object object)
    {
        return ReportedBean.forTarget(object).getMBeanInfo();
    }

    @Override
    protected Object getAttribute(Object object, String attributeName)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        return ReportedBean.forTarget(object).getAttribute(attributeName);
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*operation\\(\\)")
    public void testNonAttribute()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public int operation()
            {
                return 3;
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*operation\\(int\\)")
    public void testSetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public void operation(int param)
            {
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-getter .*getVoid\\(\\)")
    public void testInvalidGetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public void getVoid()
            {
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-numeric, non-boolean getter .*getString\\(\\)")
    public void testStringGetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public String getString()
            {
                return "0";
            }
        });
    }

    @Test(expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "report annotation on non-numeric, non-boolean getter .*getObject\\(\\)")
    public void testObjectGetter()
    {
        ReportedBean.forTarget(new Object() {
            @Reported
            public Object getObject()
            {
                return 0;
            }
        });
    }
}
