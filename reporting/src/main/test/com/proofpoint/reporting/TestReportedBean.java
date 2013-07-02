package com.proofpoint.reporting;

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
}
