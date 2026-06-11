package io.airlift.api;

import io.airlift.api.ApiOrderBy.Ordering;
import io.airlift.api.responses.ApiBadRequest;
import io.airlift.api.servertests.typedorderby.TestTypedApiOrderBy.WireOrderByField;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.airlift.api.ApiOrderByDirection.ASCENDING;
import static io.airlift.api.ApiOrderByDirection.DESCENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestApiOrderBy
{
    @Test
    public void testValidateStrings()
    {
        ApiOrderBy<String> orderBy = new ApiOrderBy<>("name", ASCENDING);
        orderBy.validate("name", "size");
        orderBy.validate(Set.of("name", "size"));

        WebApplicationException varargsException = assertThrows(WebApplicationException.class, () -> orderBy.validate("size"));
        assertThat(varargsException.getResponse().getStatus()).isEqualTo(400);
        assertThat(varargsException.getResponse().getEntity()).isEqualTo(new ApiBadRequest(Optional.of("Invalid order by: name"), Optional.empty()));

        WebApplicationException setException = assertThrows(WebApplicationException.class, () -> orderBy.validate(Set.of("size")));
        assertThat(setException.getResponse().getEntity()).isEqualTo(varargsException.getResponse().getEntity());
    }

    @Test
    public void testValidateEnums()
    {
        ApiOrderBy<Field> orderBy = new ApiOrderBy<>(Field.NAME, DESCENDING);
        orderBy.validate(Field.NAME, Field.SIZE);
        orderBy.validate(Set.of(Field.NAME, Field.SIZE));

        WebApplicationException varargsException = assertThrows(WebApplicationException.class, () -> orderBy.validate(Field.SIZE));
        assertThat(varargsException.getResponse().getStatus()).isEqualTo(400);
        assertThat(varargsException.getResponse().getEntity()).isEqualTo(new ApiBadRequest(Optional.of("Invalid order by: NAME"), Optional.empty()));

        WebApplicationException setException = assertThrows(WebApplicationException.class, () -> orderBy.validate(Set.of(Field.SIZE)));
        assertThat(setException.getResponse().getEntity()).isEqualTo(varargsException.getResponse().getEntity());
    }

    @Test
    public void testImmutableOrderings()
    {
        Ordering<String> ordering = new Ordering<>("name", ASCENDING);
        List<Ordering<String>> orderings = new ArrayList<>();
        orderings.add(ordering);
        ApiOrderBy<String> orderBy = new ApiOrderBy<>(orderings);

        orderings.clear();
        assertThat(orderBy.orderings()).containsExactly(ordering);
        assertThatThrownBy(() -> orderBy.orderings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testNullOrderings()
    {
        assertThatThrownBy(() -> new ApiOrderBy<String>(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("orderings is null");
        assertThatThrownBy(() -> new ApiOrderBy<String>(null, ASCENDING))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("field is null");
        assertThatThrownBy(() -> new ApiOrderBy<>("name", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void testToQueryValue()
    {
        assertThat(new Ordering<>("name", ASCENDING).toQueryValue()).isEqualTo("name ASC");
        assertThat(new Ordering<>("size", DESCENDING).toQueryValue()).isEqualTo("size DESC");
        assertThat(new Ordering<Object>("name", ASCENDING).toQueryValue()).isEqualTo("name ASC");
    }

    @Test
    public void testEnumToQueryValue()
    {
        ApiEnumValueResolver resolver = ApiBuilderConfig.jackson().enumValueResolver();
        assertThat(new Ordering<>(WireOrderByField.DISPLAY_NAME, ASCENDING).toQueryValue(resolver)).isEqualTo("DISPLAY_LABEL ASC");
        assertThat(new Ordering<>(WireOrderByField.CREATED_AT, DESCENDING).toQueryValue(resolver)).isEqualTo("CREATION_TIME DESC");
        assertThat(new Ordering<>(Field.SIZE, DESCENDING).toQueryValue(resolver)).isEqualTo("NAME DESC");
    }

    @Test
    public void testConfiguredResolverToQueryValue()
    {
        ApiEnumValueResolver resolver = new ApiEnumValueResolver()
        {
            @Override
            public List<String> values(Class<?> enumClass)
            {
                return List.of("CUSTOM_FIELD");
            }

            @Override
            public String value(Enum<?> value)
            {
                return "CUSTOM_FIELD";
            }
        };

        assertThat(new Ordering<>(Field.NAME, ASCENDING).toQueryValue(resolver)).isEqualTo("CUSTOM_FIELD ASC");
        assertThat(new Ordering<>(Field.NAME, DESCENDING).toQueryValue(resolver)).isEqualTo("CUSTOM_FIELD DESC");
    }

    @Test
    public void testWrongToQueryValueOverload()
    {
        assertThatThrownBy(() -> new Ordering<>(Field.NAME, ASCENDING).toQueryValue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("toQueryValue() requires a String field");
        assertThatThrownBy(() -> new Ordering<>("name", ASCENDING).toQueryValue(ApiBuilderConfig.jackson().enumValueResolver()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("toQueryValue(ApiEnumValueResolver) requires an enum field");
    }

    @Test
    public void testNullToQueryValueResolver()
    {
        assertThatThrownBy(() -> new Ordering<>(Field.NAME, ASCENDING).toQueryValue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("enumValueResolver is null");
    }

    private enum Field
    {
        NAME,
        SIZE {
            @Override
            public String toString()
            {
                return NAME.name();
            }
        },
    }
}
