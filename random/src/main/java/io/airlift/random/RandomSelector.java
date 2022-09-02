/*
 * Copyright Starburst Data, Inc. All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF STARBURST DATA.
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 *
 * Redistribution of this material is strictly prohibited.
 */
package io.airlift.random;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

public interface RandomSelector<T>
{
    T next();

    static <T> RandomSelector<T> weighted(Collection<T> collection, ToDoubleFunction<T> weightFunction, Random random)
    {
        return new WeightedRandomSelector<T>(collection, weightFunction, random::nextDouble);
    }

    static <T> RandomSelector<T> weighted(Collection<T> collection, ToDoubleFunction<T> weightFunction)
    {
        return new WeightedRandomSelector<T>(collection, weightFunction, () -> ThreadLocalRandom.current().nextDouble());
    }
}
