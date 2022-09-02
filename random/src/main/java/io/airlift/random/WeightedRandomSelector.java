package io.airlift.random;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class WeightedRandomSelector<T>
        implements RandomSelector<T>
{
    private final DoubleSupplier nextRandomDouble;
    private final List<T> objects;
    private final double[] weights;
    private final double totalWeight;

    @VisibleForTesting
    WeightedRandomSelector(Collection<T> collection, ToDoubleFunction<T> weightFunction, DoubleSupplier nextRandomDouble)
    {
        this.nextRandomDouble = requireNonNull(nextRandomDouble, "nextRandomDouble is null");

        int size = collection.size();
        checkArgument(size > 0, "empty collection");
        objects = new ArrayList<>(size);
        weights = new double[size];

        double totalWeight = 0.0;
        int i = 0;
        for (T object : collection) {
            double weight = weightFunction.applyAsDouble(object);
            objects.add(object);
            weights[i] = weight;
            totalWeight += weight;
            i++;
        }
        this.totalWeight = totalWeight;
    }

    @Override
    public T next()
    {
        double randomValue = nextRandomDouble.getAsDouble() * totalWeight;
        for (int i = 0; i < objects.size(); i++) {
            if (weights[i] >= randomValue) {
                return objects.get(i);
            }
            randomValue -= weights[i];
        }
        // maybe there is slight chance to get here due to rounding errors in floating point arithmetic
        return objects.get(objects.size() - 1);
    }
}
