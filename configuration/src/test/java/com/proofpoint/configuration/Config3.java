package com.proofpoint.configuration;

interface Config3
{
    // required
    @Config("option")
    public String getOption();

    public abstract String getOption2();
}