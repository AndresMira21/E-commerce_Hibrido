package com.ecommerce.hibrido.analytics.model;

import java.math.BigDecimal;

public record AnalyticsSnapshot(long total_orders, BigDecimal total_amount, BigDecimal average_amount) { }
