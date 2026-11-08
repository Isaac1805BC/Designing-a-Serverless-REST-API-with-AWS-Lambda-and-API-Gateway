package edu.eci.tdse.orders.lambda;

import edu.eci.tdse.orders.application.InMemoryOrderRepository;
import edu.eci.tdse.orders.application.OrderService;

/**
 * Holds the OrderService for the lifetime of a Lambda execution environment.
 * Being static, it survives between warm invocations (and is lost on a cold start).
 */
public final class OrdersRuntime {
    private static final OrderService SERVICE = new OrderService(InMemoryOrderRepository.withSeedData());

    private OrdersRuntime() {
    }

    public static OrderService service() {
        return SERVICE;
    }
}
