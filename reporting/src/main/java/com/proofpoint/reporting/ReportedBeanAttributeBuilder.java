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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.isValidGetter;

class ReportedBeanAttributeBuilder
{
    private final static Pattern getterOrSetterPattern = Pattern.compile("(get|set|is)(.+)");
    private static final Set<Class<?>> PRIMITIVE_NUMBERS = ImmutableSet.<Class<?>>of(byte.class, short.class, int.class, long.class, float.class, double.class);
    private Object target;
    private String name;
    private Method concreteGetter;
    private Method annotatedGetter;

    public ReportedBeanAttributeBuilder onInstance(Object target)
    {
        checkNotNull(target, "target is null");
        this.target = target;
        return this;
    }

    public ReportedBeanAttributeBuilder named(String name)
    {
        checkNotNull(name, "name is null");
        this.name = name;
        return this;
    }

    public ReportedBeanAttributeBuilder withConcreteGetter(Method concreteGetter)
    {
        checkNotNull(concreteGetter, "concreteGetter is null");
        checkArgument(isValidGetter(concreteGetter), "Method is not a valid getter: " + concreteGetter);
        this.concreteGetter = concreteGetter;
        return this;
    }

    public ReportedBeanAttributeBuilder withAnnotatedGetter(Method annotatedGetter)
    {
        checkNotNull(annotatedGetter, "annotatedGetter is null");
        checkArgument(isValidGetter(annotatedGetter), "Method is not a valid getter: " + annotatedGetter);
        this.annotatedGetter = annotatedGetter;
        return this;
    }

    public Collection<? extends ReportedBeanAttribute> build()
    {
        checkArgument(target != null, "JmxAttribute must have a target object");

        if (AnnotationUtils.isFlatten(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Flattened JmxAttribute must have a concrete getter");

            Object value = null;
            try {
                value = concreteGetter.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value == null) {
                return Collections.emptySet();
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value);
            ImmutableList.Builder<ReportedBeanAttribute> builder = ImmutableList.builder();
            for (ReportedBeanAttribute attribute : reportedBean.getAttributes()) {
                builder.add(new FlattenReportedBeanAttribute(name, concreteGetter, attribute));
            }
            return builder.build();
        }
        else if (AnnotationUtils.isNested(annotatedGetter)) {
            checkArgument(concreteGetter != null, "Nested JmxAttribute must have a concrete getter");

            Object value = null;
            try {
                value = concreteGetter.invoke(target);
            }
            catch (Exception ignored) {
                // todo log me
            }
            if (value == null) {
                return Collections.emptySet();
            }

            ReportedBean reportedBean = ReportedBean.forTarget(value);
            ImmutableList.Builder<ReportedBeanAttribute> builder = ImmutableList.builder();
            for (ReportedBeanAttribute attribute : reportedBean.getAttributes()) {
                builder.add(new NestedReportedBeanAttribute(name, concreteGetter, attribute));
            }
            return builder.build();
        }
        else {
            checkArgument (concreteGetter != null, "JmxAttribute must have a concrete getter");

            Class<?> attributeType;
            attributeType = concreteGetter.getReturnType();

            Descriptor descriptor = null;
            if (annotatedGetter != null) {
                descriptor = AnnotationUtils.buildDescriptor(annotatedGetter);
            }

            MBeanAttributeInfo mbeanAttributeInfo = new MBeanAttributeInfo(
                    name,
                    attributeType.getName(),
                    null,
                    true,
                    false,
                    concreteGetter.getName().startsWith("is"),
                    descriptor);

            if (Boolean.class.isAssignableFrom(attributeType) || attributeType == boolean.class) {
                return ImmutableList.of(new BooleanReportedBeanAttribute(mbeanAttributeInfo, target, concreteGetter));
            }

            return ImmutableList.of(new ObjectReportedBeanAttribute(mbeanAttributeInfo, target, concreteGetter));
        }
    }
}
