/*
 *  Copyright 2010 Dain Sundstrom
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.proofpoint.reporting;

import com.proofpoint.stats.Bucketed;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.getAttributeName;
import static com.proofpoint.reporting.ReflectionUtils.isGetter;

class ReportedBean
{
    static Method GET_PREVIOUS_BUCKET;

    private final MBeanInfo mbeanInfo;
    private final Map<String, ReportedBeanAttribute> attributes;

    static {
        try {
            Method getPreviousBucket = Bucketed.class.getDeclaredMethod("getPreviousBucket");
            getPreviousBucket.setAccessible(true);
            GET_PREVIOUS_BUCKET = getPreviousBucket;
        }
        catch (NoSuchMethodException ignored) {
            GET_PREVIOUS_BUCKET = null;
        }
    }

    public ReportedBean(String className, Collection<ReportedBeanAttribute> attributes)
    {
        List<MBeanAttributeInfo> attributeInfos = new ArrayList<>();
        Map<String, ReportedBeanAttribute> attributesBuilder = new TreeMap<>();
        for (ReportedBeanAttribute attribute : attributes) {
            attributesBuilder.put(attribute.getName(), attribute);
            attributeInfos.add(attribute.getInfo());
        }
        this.attributes = Collections.unmodifiableMap(attributesBuilder);

        mbeanInfo = new MBeanInfo(className,
                null,
                attributeInfos.toArray(new MBeanAttributeInfo[attributeInfos.size()]),
                new ModelMBeanConstructorInfo[0],
                null,
                new ModelMBeanNotificationInfo[0]);
    }

    public MBeanInfo getMBeanInfo()
    {
        return mbeanInfo;
    }

    public Collection<ReportedBeanAttribute> getAttributes()
    {
        return attributes.values();
    }

    public Number getAttribute(String name)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        checkNotNull(name, "name is null");
        ReportedBeanAttribute mbeanAttribute = attributes.get(name);
        if (mbeanAttribute == null) {
            throw new AttributeNotFoundException(name);
        }
        return mbeanAttribute.getValue();
    }

    public static ReportedBean forTarget(Object target)
    {
        checkNotNull(target, "target is null");

        List<ReportedBeanAttribute> attributes = new ArrayList<>();

        if (target instanceof Bucketed) {

            Object value = null;
            try {
                value = GET_PREVIOUS_BUCKET.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value != null) {
                ReportedBean reportedBean = ReportedBean.forTarget(value);
                for (ReportedBeanAttribute attribute : reportedBean.getAttributes()) {
                    attributes.add(new BucketedReportedBeanAttribute(target, attribute));
                }
            }
        }

        Map<String, ReportedBeanAttributeBuilder> attributeBuilders = new TreeMap<>();

        for (Map.Entry<Method, Method> entry : AnnotationUtils.findReportedMethods(target.getClass()).entrySet()) {
            Method concreteMethod = entry.getKey();
            Method annotatedMethod = entry.getValue();

            if (!isGetter(concreteMethod)) {
                throw new RuntimeException("report annotation on non-getter " + annotatedMethod.toGenericString());
            }

            String attributeName = getAttributeName(concreteMethod);

            ReportedBeanAttributeBuilder attributeBuilder = attributeBuilders.get(attributeName);
            if (attributeBuilder == null) {
                attributeBuilder = new ReportedBeanAttributeBuilder().named(attributeName).onInstance(target);
            }

            attributeBuilder = attributeBuilder
                    .withConcreteGetter(concreteMethod)
                    .withAnnotatedGetter(annotatedMethod);

            attributeBuilders.put(attributeName, attributeBuilder);
        }

        String className = target.getClass().getName();

        for (ReportedBeanAttributeBuilder attributeBuilder : attributeBuilders.values()) {
            attributes.addAll(attributeBuilder.build());
        }

        return new ReportedBean(className, attributes);
    }
}
