package com.proofpoint.sample;

import org.testng.annotations.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;

public class TestValidationExceptionMapper
{
    @Test
    public void testBasic()
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ValidationException exception = Errors.forViolations(validator.validate(new Bean())).getException();

        ValidationExceptionMapper mapper = new ValidationExceptionMapper();
        Response response = mapper.toResponse(exception);

        assertEquals(response.getStatus(), Response.Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getMetadata().get("Content-Type"), asList(MediaType.APPLICATION_JSON_TYPE));
        assertInstanceOf(response.getEntity(), List.class);
        assertEquals(((List<String>) response.getEntity()).size(), 2);
    }

    public static class Bean
    {
        @NotNull
        public String getFoo()
        {
            return null;
        }

        @Pattern(regexp="[a-z]+")
        public String getBar()
        {
            return "abcd1234";
        }
    }
}
