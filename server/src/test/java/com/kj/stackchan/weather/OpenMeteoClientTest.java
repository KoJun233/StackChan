package com.kj.stackchan.weather;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMeteoClientTest {

    private HttpServer server;
    private URI endpoint;
    private final AtomicReference<String> query = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/forecast");
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void readsOnlyTheBoundedCurrentAndTwoDayForecast() {
        server.createContext("/v1/forecast", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = validResponse().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        OpenMeteoClient client = new OpenMeteoClient(
                HttpClient.newHttpClient(), new ObjectMapper(), endpoint
        );

        OpenMeteoClient.Forecast forecast = client.fetch(31.2304, 121.4737, ZoneId.of("Asia/Shanghai"));

        assertThat(forecast.current().temperature()).isEqualTo(30.2);
        assertThat(forecast.daily()).hasSize(2);
        assertThat(forecast.daily().getFirst().precipitationProbabilityMax()).isEqualTo(65);
        assertThat(query.get())
                .contains("forecast_days=2")
                .contains("timezone=Asia%2FShanghai")
                .contains("current=temperature_2m,apparent_temperature,precipitation,weather_code")
                .doesNotContain("hourly=");
    }

    @Test
    void rejectsUnknownWeatherCodesAsInvalidResponses() {
        server.createContext("/v1/forecast", exchange -> {
            byte[] body = validResponse().replace("\"weather_code\":2", "\"weather_code\":4")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        OpenMeteoClient client = new OpenMeteoClient(
                HttpClient.newHttpClient(), new ObjectMapper(), endpoint
        );

        assertThatThrownBy(() -> client.fetch(31.2304, 121.4737, ZoneId.of("Asia/Shanghai")))
                .isInstanceOf(WorkdayWeatherUnavailableException.class)
                .extracting(error -> ((WorkdayWeatherUnavailableException) error).getFailureCode())
                .isEqualTo(WorkdayWeatherFailureCode.INVALID_RESPONSE);
    }

    private String validResponse() {
        return """
                {
                  "timezone":"Asia/Shanghai",
                  "current":{
                    "time":"2026-08-25T23:00",
                    "temperature_2m":30.2,
                    "apparent_temperature":34.1,
                    "precipitation":0.0,
                    "weather_code":2
                  },
                  "daily":{
                    "time":["2026-08-25","2026-08-26"],
                    "weather_code":[61,2],
                    "temperature_2m_max":[33.0,32.0],
                    "temperature_2m_min":[26.0,25.0],
                    "apparent_temperature_max":[38.0,36.0],
                    "apparent_temperature_min":[29.0,28.0],
                    "precipitation_probability_max":[65,20],
                    "precipitation_sum":[4.2,0.0]
                  }
                }
                """;
    }
}
