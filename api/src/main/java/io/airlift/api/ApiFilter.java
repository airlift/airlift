package io.airlift.api;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import static io.airlift.api.responses.ApiException.badRequest;
import static java.util.Objects.requireNonNull;

public record ApiFilter(Optional<Object> value)
{
    public ApiFilter
    {
        requireNonNull(value, "value is null");
    }

    public <T> Optional<T> map(Function<String, T> mapper)
    {
        return value.map(v -> {
            String str = String.valueOf(v);
            try {
                return mapper.apply(str);
            }
            catch (Exception e) {
                throw badRequest("Invalid filter: " + str);
            }
        });
    }

    public Optional<String> mapAsString()
    {
        return map(String::valueOf);
    }

    public Optional<Integer> mapAsInt()
    {
        return map(Integer::parseInt);
    }

    public Optional<Double> mapAsDouble()
    {
        return map(Double::parseDouble);
    }

    public Optional<Instant> mapAsInstant()
    {
        return map(Instant::parse);
    }

    public Optional<Boolean> mapAsBoolean()
    {
        return map(Boolean::parseBoolean);
    }
}
