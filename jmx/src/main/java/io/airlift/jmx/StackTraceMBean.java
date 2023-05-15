/*
 * Copyright 2010 Proofpoint, Inc.
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
package io.airlift.jmx;

import com.google.inject.Inject;
import org.weakref.jmx.Managed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StackTraceMBean
{
    @Inject
    public StackTraceMBean()
    {
    }

    @Managed
    public List<String> getStackTrace()
    {
        List<String> output = new ArrayList<>();
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            output.add(entry.getKey().toString());
            for (StackTraceElement element : entry.getValue()) {
                output.add("\t" + element.toString());
            }
            output.add("");
        }

        return output;
    }
}
