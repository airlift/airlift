package com.proofpoint.http.client;

import com.proofpoint.configuration.Config;

import javax.validation.constraints.Min;

public class HttpClientConfig
{
    private int workerThreads = 16;

    @Min(1)
    public int getWorkerThreads()
    {
        return workerThreads;
    }

    @Config("http-client.threads")
    public void setWorkerThreads(int workerThreads)
    {
        this.workerThreads = workerThreads;
    }
}
