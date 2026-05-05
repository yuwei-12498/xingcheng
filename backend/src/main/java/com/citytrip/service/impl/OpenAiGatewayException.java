package com.citytrip.service.impl;

public class OpenAiGatewayException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;
    private final String endpoint;
    private final String model;
    private final boolean timeout;
    private final boolean networkError;

    private OpenAiGatewayException(String message,
                                   int statusCode,
                                   String responseBody,
                                   String endpoint,
                                   String model,
                                   boolean timeout,
                                   boolean networkError,
                                   Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.endpoint = endpoint;
        this.model = model;
        this.timeout = timeout;
        this.networkError = networkError;
    }

    public static OpenAiGatewayException http(int statusCode, String responseBody, String endpoint, String model) {
        return new OpenAiGatewayException(
                "HTTP error " + statusCode + ". model=" + model + ", endpoint=" + endpoint,
                statusCode,
                responseBody,
                endpoint,
                model,
                false,
                false,
                null
        );
    }

    public static OpenAiGatewayException timeout(String endpoint, String model, Throwable cause) {
        return new OpenAiGatewayException(
                "Model request timeout. model=" + model + ", endpoint=" + endpoint,
                0,
                "",
                endpoint,
                model,
                true,
                false,
                cause
        );
    }

    public static OpenAiGatewayException network(String endpoint, String model, Throwable cause) {
        return new OpenAiGatewayException(
                "Model request network failure. model=" + model + ", endpoint=" + endpoint,
                0,
                "",
                endpoint,
                model,
                false,
                true,
                cause
        );
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getModel() {
        return model;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public boolean isNetworkError() {
        return networkError;
    }
}
