package edu.eci.tdse.orders.lambda.direct;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.domain.CreateOrderRequest;
import edu.eci.tdse.orders.domain.Order;
import edu.eci.tdse.orders.lambda.OrdersRuntime;

/**
 * Part II / Architecture A. The Lambda runtime deserializes the JSON input into a
 * CreateOrderRequest and serializes the returned Order back to JSON.
 *
 * Handler: edu.eci.tdse.orders.lambda.direct.CreateOrderHandler::handleRequest
 */
public class CreateOrderHandler implements RequestHandler<CreateOrderRequest, Order> {
    private final OrderService service;

    public CreateOrderHandler() {
        this(OrdersRuntime.service());
    }

    public CreateOrderHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public Order handleRequest(CreateOrderRequest input, Context context) {
        try {
            return service.createOrder(input);
        } catch (Exception e) {
            throw TaggedApiException.from(e);
        }
    }
}
