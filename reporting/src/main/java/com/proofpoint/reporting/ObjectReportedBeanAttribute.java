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

import javax.annotation.Nullable;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.lang.reflect.Method;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.reporting.ReflectionUtils.invoke;

class ObjectReportedBeanAttribute implements ReportedBeanAttribute
{
    private final MBeanAttributeInfo info;
    private final Object target;
    private final String name;
    private final Method getter;

    public ObjectReportedBeanAttribute(MBeanAttributeInfo info, Object target, Method getter)
    {
        this.info = checkNotNull(info, "info is null");
        this.target = checkNotNull(target, "target is null");
        this.name = info.getName();
        this.getter = checkNotNull(getter, "getter is null");
    }

    public MBeanAttributeInfo getInfo()
    {
        return info;
    }

    public String getName()
    {
        return name;
    }

    public Object getValue(@Nullable Object target)
            throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        return invoke(firstNonNull(target, this.target), getter);
    }
}