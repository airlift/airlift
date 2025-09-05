package io.airlift.api;

import com.google.common.collect.ImmutableList;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.api.responses.ApiException.badRequest;

public record ApiFilterList(List<Object> values)
{
    public ApiFilterList
    {
        values = ImmutableList.copyOf(values);
    }

    public <T> List<T> map(Function<String, T> mapper)
    {
        return values.stream().map(v -> {
            String str = String.valueOf(v);
            try {
                return mapper.apply(str);
            }
            catch (Exception e) {
                throw badRequest("Invalid filter: " + str);
            }
        }).collect(toImmutableList());
    }

    public List<String> mapAsString()
    {
        return map(String::valueOf);
    }

    public List<Integer> mapAsInt()
    {
        return map(Integer::parseInt);
    }

    public List<Double> mapAsDouble()
    {
        return map(Double::parseDouble);
    }

    public List<Instant> mapAsInstant()
    {
        return map(Instant::parse);
    }

    public List<Boolean> mapAsBoolean()
    {
        return map(Boolean::parseBoolean);
    }
}
