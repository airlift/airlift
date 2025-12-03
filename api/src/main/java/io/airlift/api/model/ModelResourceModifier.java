package io.airlift.api.model;

import java.util.Collection;

public enum ModelResourceModifier
{
    VOID,
    OPTIONAL,
    READ_ONLY,
    PATCH,
    HAS_VERSION,
    HAS_RESOURCE_ID,
    IS_UNWRAPPED,
    TOP_LEVEL_READ_ONLY,
    IS_STREAMING_RESPONSE,
    IS_MULTIPART_FORM,
    MULTIPART_RESOURCE_IS_FIRST_ITEM,
    RECURSIVE_REFERENCE;

    public static boolean hasReadOnly(Collection<ModelResourceModifier> modifiers)
    {
        return modifiers.stream().anyMatch(modifier -> (modifier == READ_ONLY) || (modifier == TOP_LEVEL_READ_ONLY));
    }
}
