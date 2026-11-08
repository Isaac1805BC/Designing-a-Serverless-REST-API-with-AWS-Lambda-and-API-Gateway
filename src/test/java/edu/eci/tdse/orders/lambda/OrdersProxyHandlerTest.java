package edu.eci.tdse.orders.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.tdse.orders.application.InMemoryOrderRepository;
import edu.eci.tdse.orders.application.OrderService;
import edu.eci.tdse.orders.lambda.proxy.OrdersProxyHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrdersProxyHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private OrdersProxyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrdersProxyHandler(new OrderService(InMemoryOrderRepository.withSeedData()));
    }

    private APIGatewayProxyRequestEvent request(String method, String resource) {
        return new APIGatewayProxyRequestEvent().withHttpMethod(method).withResource(resource);
    }

    private JsonNode body(APIGatewayProxyResponseEvent response) throws Exception {
        return MAPPER.readTree(response.getBody());
    }

    @Test
    void getExistingOrderReturns200() throws Exception {
        var event = request("GET", "/orders/{id}").withPathParamters(Map.of("id", "ORD-42"));
        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
        assertEquals("Keyboard", body(response).get("product").asText());
    }

    @Test
    void getMissingOrderReturns404WithErrorBody() throws Exception {
        var event = request("GET", "/orders/{id}").withPathParamters(Map.of("id", "ORD-999"));
        var response = handler.handleRequest(event, null);

        assertEquals(404, response.getStatusCode());
        assertEquals("ORDER_NOT_FOUND", body(response).get("error").asText());
        assertEquals("Order ORD-999 does not exist", body(response).get("message").asText());
    }

    @Test
    void postValidOrderReturns201WithLocation() throws Exception {
        var event = request("POST", "/orders")
                .withBody("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}");
        var response = handler.handleRequest(event, null);

        assertEquals(201, response.getStatusCode());
        String id = body(response).get("id").asText();
        assertEquals("/orders/" + id, response.getHeaders().get("Location"));
    }

    @Test
    void postInvalidQuantityReturns400() throws Exception {
        var event = request("POST", "/orders")
                .withBody("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":0}");
        var response = handler.handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
        assertEquals("INVALID_REQUEST", body(response).get("error").asText());
    }

    @Test
    void postMalformedJsonReturns400() throws Exception {
        var response = handler.handleRequest(request("POST", "/orders").withBody("{not json"), null);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void postWithoutBodyReturns400() {
        assertEquals(400, handler.handleRequest(request("POST", "/orders"), null).getStatusCode());
    }

    @Test
    void searchByCustomerReturns200WithArray() throws Exception {
        var event = request("GET", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-42"));
        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertTrue(body(response).isArray());
        assertEquals(2, body(response).size());
    }

    @Test
    void searchForCustomerWithoutOrdersReturnsEmptyArray() throws Exception {
        var event = request("GET", "/orders").withQueryStringParameters(Map.of("customerId", "CUST-100"));
        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertEquals(0, body(response).size());
    }

    @Test
    void searchWithoutCustomerIdReturns400() {
        assertEquals(400, handler.handleRequest(request("GET", "/orders"), null).getStatusCode());
    }

    @Test
    void unsupportedMethodReturns405() {
        assertEquals(405, handler.handleRequest(request("PATCH", "/orders"), null).getStatusCode());
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        OrderService exploding = new OrderService(null); // repository is null -> NullPointerException
        var event = request("GET", "/orders/{id}").withPathParamters(Map.of("id", "ORD-1"));
        var response = new OrdersProxyHandler(exploding).handleRequest(event, null);

        assertEquals(500, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", body(response).get("error").asText());
        assertFalse(response.getBody().contains("NullPointer"));
    }
}
