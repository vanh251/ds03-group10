package com.example.apigateway;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks which paths the gateway routes (docs/api.md §10). Every backend points at a closed port, so a routed
 * request fails with 5xx while a path without a route gets the gateway's own 404.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "IDENTITY_SERVICE_URL=http://localhost:1",
                "DRIVER_SERVICE_URL=http://localhost:1",
                "TRIP_SERVICE_URL=http://localhost:1",
                "MATCHING_SERVICE_URL=http://localhost:1",
                "PRICING_SERVICE_URL=http://localhost:1",
                "PAYMENT_SERVICE_URL=http://localhost:1",
                "NOTIFICATION_SERVICE_URL=http://localhost:1"
        })
class GatewayRoutingTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${local.server.port}")
    private int port;

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST,   /api/auth/login",
            "POST,   /api/auth/register/passenger",
            "POST,   /api/auth/register/driver",
            "GET,    /api/users/u1",
            "GET,    /api/drivers/d1",
            "GET,    /api/drivers/available",
            "GET,    /api/drivers/nearby",
            "PATCH,  /api/drivers/d1/status",
            "PATCH,  /api/drivers/d1/location",
            "POST,   /api/trips",
            "GET,    /api/trips/t1",
            "POST,   /api/trips/t1/accept",
            "POST,   /api/matching/r1/match",
            "POST,   /api/pricing/quote",
            "POST,   /api/payments/t1/charge",
            "GET,    /api/payments/t1",
            "GET,    /api/notifications"
    })
    void route_publicPath_forwardedToBackend(String method, String path) throws Exception {
        assertThat(send(method, path)).isNotEqualTo(404);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "POST,   /api/drivers/d1/reserve",
            "DELETE, /api/drivers/d1/reserve",
            "POST,   /internal/drivers",
            "DELETE, /internal/drivers",
            "GET,    /api/unknown"
    })
    void route_internalOrUnknownPath_returns404(String method, String path) throws Exception {
        assertThat(send(method, path)).isEqualTo(404);
    }

    private int send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
