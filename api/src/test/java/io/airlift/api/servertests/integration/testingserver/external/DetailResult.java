package io.airlift.api.servertests.integration.testingserver.external;

import io.airlift.api.ApiDescription;
import io.airlift.api.ApiResource;
import io.airlift.api.ApiResourceVersion;
import io.airlift.api.ApiUnwrapped;

@ApiResource(name = "detail", openApiAlternateName = "detailResult", description = "Detail result instance")
public record DetailResult(@ApiDescription("ID") DetailId detailId, ApiResourceVersion syncToken, @ApiUnwrapped Detail detail)
{
}
