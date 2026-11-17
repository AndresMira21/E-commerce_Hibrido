package com.ecommerce.hibrido.analytics.web;

import com.ecommerce.hibrido.analytics.model.AnalyticsSnapshot;
import com.ecommerce.hibrido.analytics.service.OrderAnalytics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsController {
    private final OrderAnalytics analytics;

    public AnalyticsController(OrderAnalytics analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/analytics")
    public AnalyticsSnapshot analytics() {
        return analytics.snapshot();
    }
}
