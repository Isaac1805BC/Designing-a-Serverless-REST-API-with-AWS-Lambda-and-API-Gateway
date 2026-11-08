package edu.eci.tdse.orders.application;

import edu.eci.tdse.orders.domain.Order;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory storage. NOT persistent: a Lambda execution environment is ephemeral, so data
 * disappears when it is recycled and is not shared between concurrent environments.
 */
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(100);

    /** Repository pre-loaded with sample data so GET works on a cold environment. */
    public static InMemoryOrderRepository withSeedData() {
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        repo.save(new Order("ORD-001", "CUST-42", "MacBook Air", 1));
        repo.save(new Order("ORD-42", "CUST-7", "Keyboard", 2));
        repo.save(new Order("ORD-43", "CUST-42", "Monitor", 1));
        return repo;
    }

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(Order::getId))
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return orders.remove(id) != null;
    }

    @Override
    public String nextId() {
        return "ORD-" + sequence.incrementAndGet();
    }
}
