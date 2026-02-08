package io.airlift.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

// see https://github.com/modelcontextprotocol/ext-apps/tree/main/examples/map-server
// map-app.html built from this source
// this class ported from server.ts with help from Claude
public class MapApp
{
    private final ObjectMapper objectMapper;

    @Inject
    public MapApp(ObjectMapper objectMapper)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @McpTool(
            name = "show-map",
            description = "Display an interactive world map zoomed to a specific bounding box. Use the GeoCode tool to find the bounding box of a location.",
            app = @McpApp(
                    resourceUri = "ui://cesium-map/mcp-app.html",
                    sourcePath = "map-app.html",
                    connectDomains = {
                            "https://*.openstreetmap.org", // OSM tiles + Nominatim geocoding
                            "https://cesium.com",
                            "https://*.cesium.com",
                            "https://__HOST__/dummy/test"}, // added just to test __HOST_ substitution
                    resourceDomains = {
                            "https://*.openstreetmap.org", // OSM map tiles (covers tile.openstreetmap.org)
                            "https://cesium.com",
                            "https://*.cesium.com"}))
    public CallToolResult mapTool(@McpDefaultValue("Lisbon, Portugal") @McpDescription("Optional label to display on the map") Optional<String> label,
            @McpDefaultValue("-9.2298") @McpDescription("Western longitude (-180 to 180)") double west,
            @McpDefaultValue("38.6914") @McpDescription("Southern latitude (-90 to 90)") double south,
            @McpDefaultValue("-9.0863") @McpDescription("Eastern longitude (-180 to 180)") double east,
            @McpDefaultValue("38.7968") @McpDescription("Northern latitude (-90 to 90)") double north)
    {
        String content = "Displaying globe at: W:%s, S:%s, E:%s, N:%s %s".formatted(
                String.format("%.4f", west),
                String.format("%.4f", south),
                String.format("%.4f", east),
                String.format("%.4f", north),
                label.orElse(""));
        return new CallToolResult(new TextContent(content))
                .withMeta(ImmutableMap.of("viewUUID", UUID.randomUUID()));
    }

    @McpTool(name = "geocode", description = "Search for places using OpenStreetMap. Returns coordinates and bounding boxes for up to 5 matches.")
    public String geocodeTool(@McpDescription("Place name or address to search for (e.g., 'Paris', 'Golden Gate Bridge', '1600 Pennsylvania Ave')") String query)
            throws Exception
    {
        return geocodeWithNominatim(query)
                .stream()
                .map(result -> "%s\n   Coordinates: %s, %s\n   Bounding box: W:%s, S:%s, E:%s, N:%s".formatted(
                        result.displayName,
                        String.format("%.6f", result.lat),
                        String.format("%.6f", result.lon),
                        String.format("%.4f", Double.parseDouble(result.boundingBox[2])),
                        String.format("%.4f", Double.parseDouble(result.boundingBox[0])),
                        String.format("%.4f", Double.parseDouble(result.boundingBox[3])),
                        String.format("%.4f", Double.parseDouble(result.boundingBox[1]))))
                .collect(Collectors.joining("\n\n"));
    }

    // Nominatim API response type
    public record NominatimResult(
            double lat,
            double lon,
            @JsonProperty("display_name")
            String displayName,
            @JsonProperty("boundingbox")
            String[] boundingBox) {}

    // Rate limiting for Nominatim (1 request per second per their usage policy)
    private volatile long lastNominatimRequest;
    private static final long NOMINATIM_RATE_LIMIT_MS = 1100; // 1.1 seconds to be safe

    /**
     * Query Nominatim geocoding API with rate limiting
     */
    private List<NominatimResult> geocodeWithNominatim(String query)
            throws Exception
    {
        // Respect rate limit
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastNominatimRequest;
        if (timeSinceLastRequest < NOMINATIM_RATE_LIMIT_MS) {
            Thread.sleep(NOMINATIM_RATE_LIMIT_MS - timeSinceLastRequest);
        }
        lastNominatimRequest = System.currentTimeMillis();

        String params = String.format(
                "q=%s&format=json&limit=5",
                URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        URL url = URI.create("https://nominatim.openstreetmap.org/search?" + params).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "MCP-CesiumMap-Example/1.0 (https://github.com/modelcontextprotocol)");

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Nominatim API error: " + responseCode + " " + conn.getResponseMessage());
        }

        try (InputStream is = conn.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }
}
