package com.proofpoint.configuration;

interface LegacyConfig3
{
    // required
    @Config("option")
    public String getOption();

    public abstract String getOption2();
}