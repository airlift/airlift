package io.airlift.api;

import static java.util.Objects.requireNonNull;

public enum ApiOrderByDirection
{
    ASCENDING("ASC", ">"),
    DESCENDING("DESC", "<");

    private final String value;
    private final String operator;

    public String value()
    {
        return value;
    }

    public String operator()
    {
        return operator;
    }

    ApiOrderByDirection(String value, String operator)
    {
        this.value = requireNonNull(value, "value is null");
        this.operator = requireNonNull(operator, "operator is null");
    }
}
