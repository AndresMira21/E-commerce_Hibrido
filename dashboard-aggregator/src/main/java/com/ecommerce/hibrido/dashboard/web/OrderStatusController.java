package com.ecommerce.hibrido.dashboard.web;

import com.ecommerce.hibrido.dashboard.model.OrderStatus;
import com.ecommerce.hibrido.dashboard.service.OrderStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrderStatusController {
    private final OrderStatusService statusService;

    public OrderStatusController(OrderStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/orders/status")
    public List<OrderStatus> statuses() {
        return statusService.allStatuses();
    }
}
