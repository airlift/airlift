package com.proofpoint.experimental.http.client;

import java.net.URI;
import java.util.List;

public interface HttpServiceSelector
{
    List<URI> selectHttpService();
}
