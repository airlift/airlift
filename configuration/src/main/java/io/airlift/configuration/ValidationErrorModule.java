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
package io.airlift.configuration;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.spi.Message;

import java.util.List;

public class ValidationErrorModule
        implements Module
{
    private final List<Message> messages;

    public ValidationErrorModule(List<Message> messages)
    {
        this.messages = ImmutableList.copyOf(messages);
    }

    @Override
    public void configure(Binder binder)
    {
        messages.forEach(binder::addError);
    }
}
