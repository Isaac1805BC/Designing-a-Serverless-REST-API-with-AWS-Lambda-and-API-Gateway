package edu.eci.tdse.orders.application;

import edu.eci.tdse.orders.domain.CreateOrderRequest;
import edu.eci.tdse.orders.domain.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(InMemoryOrderRepository.withSeedData());
    }

    @Test
    void createsValidOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertNotNull(created.getId());
        assertEquals("CUST-42", created.getCustomerId());
        assertEquals("MacBook Air", created.getProduct());
        assertEquals(1, created.getQuantity());
    }

    @Test
    void createdOrdersGetDistinctIds() {
        Order a = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1));
        Order b = service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 1));
        assertNotEquals(a.getId(), b.getId());
    }

    @Test
    void createdOrderCanBeRetrieved() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-9", "Desk", 3));
        assertEquals(created, service.getOrder(created.getId()));
    }

    @Test
    void retrievesExistingOrder() {
        Order order = service.getOrder("ORD-42");
        assertEquals("CUST-7", order.getCustomerId());
        assertEquals("Keyboard", order.getProduct());
        assertEquals(2, order.getQuantity());
    }

    @Test
    void missingOrderThrowsNotFound() {
        OrderNotFoundException ex = assertThrows(OrderNotFoundException.class, () -> service.getOrder("ORD-999"));
        assertEquals("Order ORD-999 does not exist", ex.getMessage());
    }

    @Test
    void findsOrdersByCustomer() {
        List<Order> orders = service.getOrdersByCustomer("CUST-42");
        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId().equals("CUST-42")));
    }

    @Test
    void customerWithoutOrdersGetsEmptyList() {
        assertTrue(service.getOrdersByCustomer("CUST-100").isEmpty());
    }

    @Test
    void rejectsNullRequest() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(null));
    }

    @Test
    void rejectsBlankCustomer() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(new CreateOrderRequest(" ", "Mouse", 1)));
        assertThrows(InvalidOrderException.class, () -> service.createOrder(new CreateOrderRequest(null, "Mouse", 1)));
    }

    @Test
    void rejectsBlankProduct() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(new CreateOrderRequest("CUST-1", "", 1)));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", 0)));
        assertThrows(InvalidOrderException.class, () -> service.createOrder(new CreateOrderRequest("CUST-1", "Mouse", -5)));
    }

    @Test
    void rejectsMissingSearchCriteria() {
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(null));
        assertThrows(InvalidOrderException.class, () -> service.getOrder(" "));
    }
}
