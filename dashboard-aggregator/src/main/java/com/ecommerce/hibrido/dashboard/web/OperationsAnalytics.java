package com.ecommerce.hibrido.dashboard.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
class OperationsAnalytics {
    private final RestClient analytics;

    OperationsAnalytics(@Value("${ANALYTICS_BASE_URL:http://analytics-service:8082}") String url) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(2000);
        analytics = RestClient.builder().baseUrl(url).requestFactory(timeouts).build();
    }

    @GetMapping("/analytics")
    ResponseEntity<JsonNode> snapshot() {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(analytics.get().uri("/analytics").retrieve().body(JsonNode.class));
        } catch (RestClientException unavailable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
