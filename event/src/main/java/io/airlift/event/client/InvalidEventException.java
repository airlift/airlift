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
package io.airlift.event.client;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.IOException;

public class InvalidEventException
        extends IOException
{
    @FormatMethod
    public InvalidEventException(@FormatString String message, Object... args)
    {
        super(String.format(message, args));
    }

    @FormatMethod
    public InvalidEventException(Throwable cause, @FormatString String message, Object... args)
    {
        super(String.format(message, args), cause);
    }
}
