package edu.eci.tdse.orders.application;

import edu.eci.tdse.orders.domain.CreateOrderRequest;
import edu.eci.tdse.orders.domain.Order;

import java.util.List;

/**
 * Application logic. Knows nothing about HTTP, API Gateway or AWS: it only throws
 * domain exceptions. Translating them to status codes is the adapter's job.
 */
public class OrderService {
    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(CreateOrderRequest request) {
        if (request == null) throw new InvalidOrderException("Request body is required");
        if (isBlank(request.getCustomerId())) throw new InvalidOrderException("customerId is required");
        if (isBlank(request.getProduct())) throw new InvalidOrderException("product is required");
        if (request.getQuantity() <= 0) throw new InvalidOrderException("quantity must be greater than 0");

        Order order = new Order(repository.nextId(), request.getCustomerId().trim(),
                request.getProduct().trim(), request.getQuantity());
        return repository.save(order);
    }

    public Order getOrder(String id) {
        if (isBlank(id)) throw new InvalidOrderException("id is required");
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        if (isBlank(customerId)) throw new InvalidOrderException("customerId is required");
        return repository.findByCustomerId(customerId);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
