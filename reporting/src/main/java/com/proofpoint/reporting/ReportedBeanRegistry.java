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

import com.google.common.collect.ImmutableMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.ObjectName;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Maps.newConcurrentMap;

class ReportedBeanRegistry
{
    private ConcurrentMap<ObjectName, ReportedBean> reportedBeans = newConcurrentMap();

    Map<ObjectName, ReportedBean> getReportedBeans()
    {
        return ImmutableMap.copyOf(reportedBeans);
    }

    public void register(ReportedBean reportedBean, ObjectName name)
            throws InstanceAlreadyExistsException
    {
        if (name == null) {
            throw new UnsupportedOperationException("Only explicit name supported at this time");
        }
        if (reportedBeans.putIfAbsent(name, reportedBean) != null) {
            throw new InstanceAlreadyExistsException(name + " is already registered");
        }
    }

    public void unregister(ObjectName name)
    {
        reportedBeans.remove(name);
    }
}
