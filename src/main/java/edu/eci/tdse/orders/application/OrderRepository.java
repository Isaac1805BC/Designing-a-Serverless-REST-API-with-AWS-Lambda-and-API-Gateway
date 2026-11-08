package edu.eci.tdse.orders.application;

import edu.eci.tdse.orders.domain.Order;

import java.util.List;
import java.util.Optional;

/** Storage port. In-memory today; a DynamoDB implementation could replace it without touching OrderService. */
public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(String id);

    List<Order> findByCustomerId(String customerId);

    /** @return true if an order was removed, false if it did not exist */
    boolean deleteById(String id);

    /** Next unique identifier, e.g. ORD-101. */
    String nextId();
}
