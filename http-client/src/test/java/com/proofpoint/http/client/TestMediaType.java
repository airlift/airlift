package com.proofpoint.http.client;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMediaType
{
    @Test
    public void test()
    {
        Assert.assertTrue(MediaType.parse("application/json; charset=UTF-8").is(MediaType.JSON_UTF_8.withoutParameters()));
        Assert.assertTrue(MediaType.parse("application/json").is(MediaType.JSON_UTF_8.withoutParameters()));
    }
}
