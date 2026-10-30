package com.api.v1.bearerauth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/v1/maps")
public class GoogleMapsController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @GetMapping("/autocomplete")
    public Map<String, Object> autocomplete(@RequestParam String input) {
        if (input == null || input.trim().length() < 2) return Map.of("suggestions", List.of());
        ObjectNode body = mapper.createObjectNode();
        body.put("input", input.trim());
        body.putArray("includedRegionCodes").add("kh");
        JsonNode result = google("https://places.googleapis.com/v1/places:autocomplete", "POST", body.toString(),
                Map.of("X-Goog-FieldMask", "suggestions.placePrediction.placeId,suggestions.placePrediction.text.text"));
        List<Map<String, String>> suggestions = new ArrayList<>();
        for (JsonNode row : result.path("suggestions")) {
            JsonNode prediction = row.path("placePrediction");
            if (!prediction.isMissingNode()) suggestions.add(Map.of(
                    "placeId", prediction.path("placeId").asText(""),
                    "text", prediction.path("text").path("text").asText("")
            ));
        }
        return Map.of("suggestions", suggestions);
    }

    @GetMapping("/places/{placeId}")
    public Map<String, Object> place(@PathVariable String placeId) {
        String url = "https://places.googleapis.com/v1/places/" + encodePath(placeId);
        JsonNode result = google(url, "GET", null, Map.of("X-Goog-FieldMask", "id,formattedAddress,location,addressComponents"));
        JsonNode location = result.path("location");
        if (location.isMissingNode()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Maps did not return coordinates for this place.");
        return Map.of("address", result.path("formattedAddress").asText(""),
                "latitude", location.path("latitude").asDouble(),
                "longitude", location.path("longitude").asDouble(),
                "city", cityFromComponents(result.path("addressComponents")));
    }

    @GetMapping("/reverse-geocode")
    public Map<String, Object> reverseGeocode(@RequestParam double latitude, @RequestParam double longitude) {
        String url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=" + latitude + "," + longitude;
        JsonNode result = google(url, "GET", null, Map.of());
        JsonNode first = result.path("results").path(0);
        if (first.isMissingNode()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No address was found at this map location.");
        return Map.of("address", first.path("formatted_address").asText(""),
                "city", cityFromGeocode(first.path("address_components")));
    }

    @GetMapping("/route")
    public Map<String, Object> route(@RequestParam double destinationLatitude, @RequestParam double destinationLongitude) {
        String originLat = System.getenv("FOODIE_RESTAURANT_LAT");
        String originLng = System.getenv("FOODIE_RESTAURANT_LNG");
        if (originLat == null || originLng == null || originLat.isBlank() || originLng.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Set FOODIE_RESTAURANT_LAT and FOODIE_RESTAURANT_LNG to your restaurant coordinates to enable delivery routes.");
        }
        String body = """
                {
                  "origin":{"location":{"latLng":{"latitude":%s,"longitude":%s}}},
                  "destination":{"location":{"latLng":{"latitude":%s,"longitude":%s}}},
                  "travelMode":"DRIVE",
                  "routingPreference":"TRAFFIC_AWARE",
                  "computeAlternativeRoutes":false,
                  "languageCode":"en",
                  "units":"METRIC"
                }
                """.formatted(originLat, originLng, destinationLatitude, destinationLongitude);
        JsonNode result = google("https://routes.googleapis.com/directions/v2:computeRoutes", "POST", body,
                Map.of("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"));
        JsonNode route = result.path("routes").path(0);
        if (route.isMissingNode()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Google Maps could not find a driving route.");
        return Map.of("distanceMeters", route.path("distanceMeters").asDouble(0),
                "duration", route.path("duration").asText(""),
                "encodedPolyline", route.path("polyline").path("encodedPolyline").asText(""));
    }

    private JsonNode google(String url, String method, String body, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (!url.contains("maps.googleapis.com/maps/api/geocode")) builder.header("X-Goog-Api-Key", apiKey());
            extraHeaders.forEach(builder::header);
            if (method.equals("POST")) {
                builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            } else {
                if (url.contains("maps.googleapis.com/maps/api/geocode")) {
                    builder.uri(URI.create(url + "&key=" + encode(apiKey())));
                }
                builder.GET();
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Google Maps request failed (HTTP " + response.statusCode() + "). Check enabled APIs and the server API key.");
            }
            if (json.has("status") && !"OK".equals(json.path("status").asText())) {
                String status = json.path("status").asText();
                String reason = json.path("error_message").asText("No additional details provided.");

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Google Geocoding failed: " + status + " - " + reason
                );
            }
            return json;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to reach Google Maps Platform.");
        }
    }

    private String apiKey() {
        String key = System.getenv("GOOGLE_MAPS_SERVER_API_KEY");
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Maps server API key is not configured. Set GOOGLE_MAPS_SERVER_API_KEY."
            );
        }
        return key;
    }

    private String cityFromComponents(JsonNode components) {
        for (JsonNode component : components) {
            JsonNode types = component.path("types");
            if (hasType(types, "locality") || hasType(types, "administrative_area_level_2")) return component.path("longText").asText("Phnom Penh");
        }
        return "Phnom Penh";
    }

    private String cityFromGeocode(JsonNode components) {
        for (JsonNode component : components) {
            JsonNode types = component.path("types");
            if (hasType(types, "locality") || hasType(types, "administrative_area_level_2")) return component.path("long_name").asText("Phnom Penh");
        }
        return "Phnom Penh";
    }

    private boolean hasType(JsonNode types, String type) {
        for (JsonNode value : types) if (type.equals(value.asText())) return true;
        return false;
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String encodePath(String value) { return value.replace("/", "%2F").replace(" ", "%20"); }
}
