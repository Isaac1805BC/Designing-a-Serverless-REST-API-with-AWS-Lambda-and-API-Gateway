package edu.eci.tdse.orders.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.tdse.orders.application.InvalidOrderException;
import edu.eci.tdse.orders.application.OrderNotFoundException;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.domain.CreateOrderRequest;
import edu.eci.tdse.orders.domain.Order;
import edu.eci.tdse.orders.lambda.ErrorResponse;
import edu.eci.tdse.orders.lambda.OrdersRuntime;

import java.util.Map;

/**
 * Architecture B: thin HTTP adapter for Lambda proxy integration (REST API, payload v1).
 * It owns everything HTTP: routing, parameter extraction, status codes, headers, JSON.
 * The business rules stay in OrderService, which never sees an HTTP concept.
 *
 * Handler: edu.eci.tdse.orders.lambda.proxy.OrdersProxyHandler::handleRequest
 */
public class OrdersProxyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    private final OrderService service;

    public OrdersProxyHandler() {
        this(OrdersRuntime.service());
    }

    public OrdersProxyHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            return route(event);
        } catch (InvalidOrderException e) {
            return error(400, "INVALID_REQUEST", e.getMessage());
        } catch (OrderNotFoundException e) {
            return error(404, "ORDER_NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            // Never leak internals to the client; log the real cause for operators.
            if (context != null && context.getLogger() != null) {
                context.getLogger().log("Unexpected error: " + e);
            }
            return error(500, "INTERNAL_ERROR", "Unexpected internal error");
        }
    }

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        String method = event.getHttpMethod();
        // 'resource' is the route template ("/orders/{id}"), independent of the concrete id.
        String resource = event.getResource();
        if (method == null || resource == null) {
            return error(400, "INVALID_REQUEST", "Malformed request");
        }

        switch (resource) {
            case "/orders":
                if ("POST".equals(method)) return createOrder(event);
                if ("GET".equals(method)) return findOrders(event);
                break;
            case "/orders/{id}":
                if ("GET".equals(method)) return getOrder(event);
                break;
            default:
                return error(404, "ROUTE_NOT_FOUND", "No route for " + method + " " + resource);
        }
        return error(405, "METHOD_NOT_ALLOWED", method + " is not supported on " + resource);
    }

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        CreateOrderRequest request;
        try {
            String body = event.getBody();
            request = (body == null || body.isBlank()) ? null : MAPPER.readValue(body, CreateOrderRequest.class);
        } catch (JsonProcessingException e) {
            return error(400, "INVALID_REQUEST", "Request body is not valid JSON");
        }
        Order created = service.createOrder(request);
        APIGatewayProxyResponseEvent response = json(201, created);
        response.setHeaders(Map.of("Content-Type", "application/json", "Location", "/orders/" + created.getId()));
        return response;
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        Map<String, String> params = event.getPathParameters();
        return json(200, service.getOrder(params == null ? null : params.get("id")));
    }

    private APIGatewayProxyResponseEvent findOrders(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        Map<String, String> query = event.getQueryStringParameters();
        return json(200, service.getOrdersByCustomer(query == null ? null : query.get("customerId")));
    }

    private APIGatewayProxyResponseEvent error(int status, String code, String message) {
        try {
            return json(status, new ErrorResponse(code, message));
        } catch (JsonProcessingException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withHeaders(JSON_HEADERS)
                    .withBody("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected internal error\"}");
        }
    }

    private APIGatewayProxyResponseEvent json(int status, Object body) throws JsonProcessingException {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(JSON_HEADERS)
                .withBody(MAPPER.writeValueAsString(body));
    }
}
