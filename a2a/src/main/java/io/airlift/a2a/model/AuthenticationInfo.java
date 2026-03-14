package io.airlift.a2a.model;

import java.util.Optional;

public record AuthenticationInfo(String scheme, Optional<String> credentials)
{
}
