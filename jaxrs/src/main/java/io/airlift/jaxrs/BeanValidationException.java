/*
 * Copyright 2012 Proofpoint, Inc.
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
package io.airlift.jaxrs;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import javax.validation.ConstraintViolation;

import java.util.Set;

import static com.google.common.collect.Iterables.transform;

/**
 * Thrown when bean validation has errors.
 */
public class BeanValidationException
        extends ParsingException
{
    private final Set<ConstraintViolation<Object>> violations;

    public BeanValidationException(Set<ConstraintViolation<Object>> violations)
    {
        super(Joiner.on(", ").join(transform(violations, constraintMessageBuilder())));
        this.violations = ImmutableSet.copyOf(violations);
    }

    public Set<ConstraintViolation<Object>> getViolations()
    {
        return violations;
    }

    public static <T> Function<ConstraintViolation<T>, String> constraintMessageBuilder()
    {
        return violation -> violation.getPropertyPath().toString() + " " + violation.getMessage();
    }
}
