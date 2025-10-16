package io.airlift.api.servertests.multipart;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.airlift.api.ApiCreate;
import io.airlift.api.ApiCustom;
import io.airlift.api.ApiMultiPart.ApiMultiPartForm;
import io.airlift.api.ApiMultiPart.ApiMultiPartFormWithResource;
import io.airlift.api.ApiMultiPart.ItemInput;
import io.airlift.api.ApiQuotaController;
import io.airlift.api.ApiService;
import io.airlift.api.ApiType;
import io.airlift.api.ServiceType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Request;

import java.io.IOException;
import java.util.Iterator;

import static io.airlift.api.servertests.multipart.TestMultiPartService.INSTANT;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unused")
@ApiService(type = ServiceType.class, name = "standard", description = "Does standard things")
public class MultiPartService
{
    static final ThePart THE_PART = new ThePart("Ryan Giggs", INSTANT, 10, ImmutableMap.of("one", "1"), new SubPart("subpart", 1234));

    private final ApiQuotaController quotaController;

    @Inject
    public MultiPartService(ApiQuotaController quotaController)
    {
        this.quotaController = requireNonNull(quotaController, "quotaController is null");
    }

    @ApiCreate(description = "dummy", quotas = "PART")
    public void createPart(@Context Request request, ApiMultiPartFormWithResource<ThePart> thePart)
            throws IOException
    {
        String expectedContent = Resources.toString(Resources.getResource("test-file.txt"), UTF_8);

        ThePart resource = thePart.resource();

        Iterator<ItemInput> iIterator = thePart.itemInputIterator();

        assertThat(iIterator.hasNext()).isTrue();
        ItemInput itemInput = iIterator.next();
        if (!itemInput.name().equals("file")) {
            throw new WebApplicationException(BAD_REQUEST);
        }
        String fileContents = new String(itemInput.inputStream().readAllBytes(), UTF_8);

        assertThat(fileContents).isEqualTo(expectedContent);
        assertThat(resource).isEqualTo(THE_PART);

        assertThat(iIterator.hasNext()).isFalse();

        quotaController.recordQuotaUsage(request, "PART");
    }

    @ApiCustom(type = ApiType.CREATE, verb = "noresource", description = "dummy", quotas = "PART")
    public void createPartWithoutResource(@Context Request request, ApiMultiPartForm<ThePart> thePart)
            throws IOException
    {
        String expectedContent = Resources.toString(Resources.getResource("test-file.txt"), UTF_8);

        Iterator<ItemInput> iIterator = thePart.itemInputIterator();

        assertThat(iIterator.hasNext()).isTrue();
        ItemInput itemInput = iIterator.next();
        if (!itemInput.name().equals("file")) {
            throw new WebApplicationException(BAD_REQUEST);
        }
        String fileContents = new String(itemInput.inputStream().readAllBytes(), UTF_8);

        assertThat(fileContents).isEqualTo(expectedContent);

        assertThat(iIterator.hasNext()).isFalse();

        quotaController.recordQuotaUsage(request, "PART");
    }
}
