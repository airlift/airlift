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

import com.google.inject.Inject;
import com.google.inject.Injector;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Set;

class GuiceReportExporter
{
    private final ReportExporter reportExporter;

    @Inject
    GuiceReportExporter(Set<Mapping> mappings, ReportExporter reportExporter, Injector injector)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        this.reportExporter = reportExporter;
        export(mappings, injector);
    }

    private void export(Set<Mapping> mappings, Injector injector)
            throws MalformedObjectNameException, InstanceAlreadyExistsException
    {
        for (Mapping mapping : mappings) {
            Object object = injector.getInstance(mapping.getKey());
            reportExporter.export(new ObjectName(mapping.getName()), object);
        }
    }
}
