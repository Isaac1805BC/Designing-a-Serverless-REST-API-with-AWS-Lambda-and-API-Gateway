package edu.eci.tdse.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.domain.Order;
import edu.eci.tdse.orders.lambda.OrdersRuntime;

import java.util.List;
import java.util.Map;

/**
 * Architecture A. API Gateway's mapping template turns GET /orders?customerId=CUST-42
 * into {"customerId": "CUST-42"}.
 *
 * Handler: edu.eci.tdse.orders.lambda.direct.FindOrdersHandler::handleRequest
 */
public class FindOrdersHandler implements RequestHandler<Map<String, String>, List<Order>> {
    private final OrderService service;

    public FindOrdersHandler() {
        this(OrdersRuntime.service());
    }

    public FindOrdersHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public List<Order> handleRequest(Map<String, String> input, Context context) {
        try {
            return service.getOrdersByCustomer(input == null ? null : input.get("customerId"));
        } catch (Exception e) {
            throw TaggedApiException.from(e);
        }
    }
}
