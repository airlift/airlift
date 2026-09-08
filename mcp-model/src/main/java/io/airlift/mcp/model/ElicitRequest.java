package io.airlift.mcp.model;

public sealed interface ElicitRequest
        permits ElicitRequestForm, ElicitRequestUrl {}
