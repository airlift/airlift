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
package com.proofpoint.units;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class MaxDurationValidator
        implements ConstraintValidator<MaxDuration, Duration>
{
    private Duration max;

    @Override
    public void initialize(MaxDuration duration)
    {
        this.max = Duration.valueOf(duration.value());
    }

    @Override
    public boolean isValid(Duration duration, ConstraintValidatorContext context)
    {
        return duration == null || duration.compareTo(max) <= 0;
    }
}
