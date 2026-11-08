package edu.eci.tdse.orders.lambda.direct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.tdse.orders.application.InvalidOrderException;
import edu.eci.tdse.orders.application.OrderNotFoundException;
import edu.eci.tdse.orders.lambda.ErrorResponse;

/**
 * Architecture A (non-proxy) cannot return a status code from Lambda: API Gateway decides it by
 * matching a regex against the exception message ("selection pattern"). So the handler throws an
 * exception whose message starts with a tag, "[404] {json error body}", and API Gateway maps
 * "^\[404\].*" to HTTP 404 and strips the tag in the response template.
 */
public class TaggedApiException extends RuntimeException {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public TaggedApiException(int status, String code, String message) {
        super("[" + status + "] " + toJson(new ErrorResponse(code, message)));
    }

    /** Translates any exception thrown by the application into a tagged exception. */
    public static TaggedApiException from(Exception e) {
        if (e instanceof InvalidOrderException) {
            return new TaggedApiException(400, "INVALID_REQUEST", e.getMessage());
        }
        if (e instanceof OrderNotFoundException) {
            return new TaggedApiException(404, "ORDER_NOT_FOUND", e.getMessage());
        }
        return new TaggedApiException(500, "INTERNAL_ERROR", "Unexpected internal error");
    }

    private static String toJson(ErrorResponse body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected internal error\"}";
        }
    }
}
