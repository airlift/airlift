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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import java.lang.reflect.Method;
import java.util.List;

final class Signature
{
    private final String actionName;
    private final List<String> parameterTypes;

    public Signature(Method method)
    {
        this.actionName = method.getName();

        Builder<String> builder = ImmutableList.builder();
        for (Class<?> type : method.getParameterTypes()) {
            builder.add(type.getName());
        }
        parameterTypes = builder.build();
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(actionName, parameterTypes);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Signature other = (Signature) obj;
        return Objects.equal(this.actionName, other.actionName) && Objects.equal(this.parameterTypes, other.parameterTypes);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append(actionName).append('(');
        boolean first = true;
        for (String type : parameterTypes) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(type);
            first = false;
        }
        sb.append(')');
        return sb.toString();
    }
}
