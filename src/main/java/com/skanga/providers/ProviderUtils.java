
// ProviderUtils.java
package com.skanga.providers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Utility class for AI provider implementations.
 * Provides common HTTP client setup and shared utilities.
 */
public class ProviderUtils {
    private static final ObjectMapper SHARED_OBJECT_MAPPER = createConfiguredMapper();

    private static ObjectMapper createConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    public static ObjectMapper getObjectMapper() {
        return SHARED_OBJECT_MAPPER;
    }

    /**
     * Creates a standardized HTTP client for all providers.
     */
    public static HttpClient createStandardHttpClient() {
        return HttpClientManager.getSharedClient();
    }

    // Add method for custom timeouts if needed
    public static HttpClient createCustomHttpClient(Duration connectTimeout) {
        return HttpClientManager.createCustomClient(connectTimeout, Duration.ofMinutes(2));
    }
    /**
     * Safely gets a string value from a map, with fallback.
     */
    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Safely gets an integer value from a map, with fallback.
     */
    public static int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ProviderUtils() {}
}
