package edu.eci.tdse.orders.lambda;

import edu.eci.tdse.orders.application.InMemoryOrderRepository;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.domain.CreateOrderRequest;
import edu.eci.tdse.orders.domain.Order;
import edu.eci.tdse.orders.lambda.direct.CreateOrderHandler;
import edu.eci.tdse.orders.lambda.direct.FindOrdersHandler;
import edu.eci.tdse.orders.lambda.direct.GetOrderHandler;
import edu.eci.tdse.orders.lambda.direct.TaggedApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Architecture A handlers: plain POJO in / POJO out, errors expressed as tagged exceptions. */
class DirectHandlersTest {
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(InMemoryOrderRepository.withSeedData());
    }

    @Test
    void createReturnsOrder() {
        Order order = new CreateOrderHandler(service).handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 1), null);
        assertEquals("CUST-42", order.getCustomerId());
    }

    @Test
    void createInvalidIsTagged400() {
        var ex = assertThrows(TaggedApiException.class,
                () -> new CreateOrderHandler(service).handleRequest(new CreateOrderRequest("CUST-42", "MacBook Air", 0), null));
        assertTrue(ex.getMessage().startsWith("[400] "));
        assertTrue(ex.getMessage().contains("INVALID_REQUEST"));
    }

    @Test
    void getReturnsOrder() {
        assertEquals("Keyboard", new GetOrderHandler(service).handleRequest(Map.of("id", "ORD-42"), null).getProduct());
    }

    @Test
    void getMissingIsTagged404() {
        var ex = assertThrows(TaggedApiException.class,
                () -> new GetOrderHandler(service).handleRequest(Map.of("id", "ORD-999"), null));
        assertTrue(ex.getMessage().startsWith("[404] "));
        assertTrue(ex.getMessage().contains("ORDER_NOT_FOUND"));
    }

    @Test
    void findReturnsCustomerOrders() {
        List<Order> orders = new FindOrdersHandler(service).handleRequest(Map.of("customerId", "CUST-42"), null);
        assertEquals(2, orders.size());
    }

    @Test
    void unexpectedErrorIsTagged500() {
        var ex = assertThrows(TaggedApiException.class,
                () -> new GetOrderHandler(new OrderService(null)).handleRequest(Map.of("id", "ORD-1"), null));
        assertTrue(ex.getMessage().startsWith("[500] "));
    }
}
