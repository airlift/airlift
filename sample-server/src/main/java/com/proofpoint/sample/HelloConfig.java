package com.proofpoint.sample;

import com.proofpoint.configuration.Config;

public class HelloConfig
{
    @Config("hello.language")
    public String getLanguage()
    {
        return "en";
    }
}
