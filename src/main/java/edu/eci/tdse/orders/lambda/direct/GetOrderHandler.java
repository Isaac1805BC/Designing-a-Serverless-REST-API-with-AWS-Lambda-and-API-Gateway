package edu.eci.tdse.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.domain.Order;
import edu.eci.tdse.orders.lambda.OrdersRuntime;

import java.util.Map;

/**
 * Architecture A. API Gateway's mapping template turns GET /orders/ORD-42 into {"id": "ORD-42"}.
 *
 * Handler: edu.eci.tdse.orders.lambda.direct.GetOrderHandler::handleRequest
 */
public class GetOrderHandler implements RequestHandler<Map<String, String>, Order> {
    private final OrderService service;

    public GetOrderHandler() {
        this(OrdersRuntime.service());
    }

    public GetOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(Map<String, String> input, Context context) {
        try {
            return service.getOrder(input == null ? null : input.get("id"));
        } catch (Exception e) {
            throw TaggedApiException.from(e);
        }
    }
}
