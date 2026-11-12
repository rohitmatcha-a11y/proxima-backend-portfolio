package com.jobportal.client.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobportal.domain.entity.ContinentMapping;
import com.jobportal.domain.entity.GeoLocationEntry;
import com.jobportal.repository.jpa.ContinentMappingRepository;
import com.jobportal.repository.jpa.GeoLocationEntryRepository;
import com.jobportal.util.SafeErrorSummary;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodingClient {

    private static final String REDIS_GEO_CACHE        = "geo:location-cache";
    private static final String REDIS_CONTINENT_CACHE   = "geo:continent-mappings";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final ContinentMappingRepository continentMappingRepository;
    private final GeoLocationEntryRepository geoLocationEntryRepository;

    @Value("${scrapers.nominatim.base-url:https://nominatim.openstreetmap.org/search}")
    private String nominatimBaseUrl;

    // ── Startup: preload all continent mappings into Redis ──

    @PostConstruct
    public void preloadContinentMappings() {
        try {
            RMap<String, String> cache = redissonClient.getMap(REDIS_CONTINENT_CACHE);
            if (cache.isEmpty()) {
                continentMappingRepository.findAll().forEach(m ->
                    cache.put(m.getCountryCode(), m.getContinent()));
                log.info("[Geocode] Preloaded {} continent mappings into Redis", cache.size());
            } else {
                log.debug("[Geocode] Continent mappings already in Redis ({})", cache.size());
            }
        } catch (Exception e) {
            log.warn("[Geocode] Failed to preload continent mappings: {}", SafeErrorSummary.from(e));
        }
    }

    // ── Geo location resolution ──

    /**
     * Resolve free-form location text to city, country and continent.
     * Priority: Redis geo cache -> DB geo_locations -> Nominatim API
     * Results stored in both DB (permanent source of truth) and Redis (fast access).
     * On Redis flush, DB is used to repopulate Redis without calling Nominatim again.
     * Continent lookup uses Redis hash (preloaded on startup from continent_mappings table).
     */
    public GeoLocation resolveLocation(String locationText) {
        if (locationText == null || locationText.isBlank()) return GeoLocation.empty();
        String key = locationText.trim().toLowerCase();

        // Step 1: Check Redis geo cache
        RMap<String, String> redisCache = redissonClient.getMap(REDIS_GEO_CACHE);
        if (redisCache.containsKey(key)) {
            return parseRedisValue(redisCache.get(key));
        }

        // Step 2: Check DB geo_locations (survives Redis flushes)
        var dbEntry = geoLocationEntryRepository.findById(key);
        if (dbEntry.isPresent()) {
            GeoLocationEntry cached = dbEntry.get();
            redisCache.put(key, toRedisValue(cached.getCity(), cached.getCountry(), cached.getContinent()));
            log.debug("[Geocode] DB hit for '{}' -> city={} country={} continent={}",
                locationText, cached.getCity(), cached.getCountry(), cached.getContinent());
            return new GeoLocation(cached.getCity(), cached.getCountry(), cached.getContinent());
        }

        // Step 3: Call Nominatim API — only for new location strings never seen before
        // For multi-location strings (e.g. "Singapore ; Taipei, Taiwan"), take first location only
        String locationToResolve = locationText.contains(";")
            ? locationText.split(";")[0].trim()
            : locationText.trim();
        try {
            String encoded = java.net.URLEncoder.encode(locationToResolve,
                java.nio.charset.StandardCharsets.UTF_8);
            String url = nominatimBaseUrl + "?q=" + encoded + "&format=json&limit=1&addressdetails=1";
            java.net.HttpURLConnection conn =
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JobzJobPortal/1.0");
            conn.setRequestProperty("Accept-Language", "en");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            String resolvedCity = null, resolvedCountry = null, resolvedContinent = null;
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    JsonNode results = objectMapper.readTree(sb.toString());
                    if (results.isArray() && results.size() > 0) {
                        JsonNode address = results.get(0).path("address");
                        String countryName = address.path("country").asText("");
                        String countryCode = address.path("country_code").asText("").toLowerCase();
                        String cityName = address.path("city").asText("");
                        if (cityName.isBlank()) cityName = address.path("town").asText("");
                        if (cityName.isBlank()) cityName = address.path("village").asText("");
                        resolvedCity = cityName.isBlank() ? null : cityName;
                        resolvedCountry = countryName.isBlank() ? null : countryName;
                        resolvedContinent = countryCode.isBlank() ? null :
                            resolveContinent(countryCode, countryName);
                    }
                }
            }
            conn.disconnect();

            // Respect Nominatim rate limit — 1 request/sec
            Thread.sleep(1100);

            // Step 4: Save to DB geo_locations — permanent source of truth
            geoLocationEntryRepository.save(GeoLocationEntry.builder()
                .locationKey(key)
                .city(resolvedCity)
                .country(resolvedCountry)
                .continent(resolvedContinent)
                .build());

            // Redis NOT written here — populated on read path (DB hit -> Redis)

            log.debug("[Geocode] Nominatim resolved '{}' -> city={} country={} continent={}",
                locationText, resolvedCity, resolvedCountry, resolvedContinent);
            return new GeoLocation(resolvedCity, resolvedCountry, resolvedContinent);

        } catch (Exception e) {
            log.debug("[Geocode] Failed for '{}': {}", locationText, SafeErrorSummary.from(e));
            geoLocationEntryRepository.save(GeoLocationEntry.builder().locationKey(key).build());
            return GeoLocation.empty();
        }
    }

    // ── Continent resolution — Redis first, then DB, auto-insert unknown ──

    private String resolveContinent(String countryCode, String countryName) {
        RMap<String, String> continentCache = redissonClient.getMap(REDIS_CONTINENT_CACHE);

        // Step 1: Check Redis continent cache
        String continent = continentCache.get(countryCode);
        if (continent != null) return continent;

        // Step 2: Check DB continent_mappings
        return continentMappingRepository.findById(countryCode)
            .map(mapping -> {
                // Repopulate Redis
                continentCache.put(countryCode, mapping.getContinent());
                return mapping.getContinent();
            })
            .orElseGet(() -> {
                // Unknown country code — insert as Unknown in both DB and Redis
                log.warn("[Geocode] Unknown country_code: {} for country: {} — inserting as Unknown",
                    countryCode, countryName);
                ContinentMapping unknown = new ContinentMapping(countryCode, "Unknown");
                continentMappingRepository.save(unknown);
                continentCache.put(countryCode, "Unknown");
                return null;
            });
    }

    // ── Helpers ──

    private GeoLocation parseRedisValue(String value) {
        String[] parts = value.split("\\|", -1);
        return new GeoLocation(
            parts.length > 0 && !parts[0].isEmpty() ? parts[0] : null,
            parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
            parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null
        );
    }

    private String toRedisValue(String city, String country, String continent) {
        return (city != null ? city : "") + "|"
             + (country != null ? country : "") + "|"
             + (continent != null ? continent : "");
    }
}
