package com.kj.stackchan.weather;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenMeteoClient {

    static final URI FORECAST_ENDPOINT = URI.create("https://api.open-meteo.com/v1/forecast");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Set<Integer> WEATHER_CODES = Set.of(
            0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
            71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99
    );
    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,precipitation,weather_code";
    private static final String DAILY_FIELDS = String.join(",",
            "weather_code",
            "temperature_2m_max",
            "temperature_2m_min",
            "apparent_temperature_max",
            "apparent_temperature_min",
            "precipitation_probability_max",
            "precipitation_sum"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI forecastEndpoint;

    @Autowired
    public OpenMeteoClient(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper,
                FORECAST_ENDPOINT
        );
    }

    OpenMeteoClient(HttpClient httpClient, ObjectMapper objectMapper, URI forecastEndpoint) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.forecastEndpoint = forecastEndpoint;
    }

    public Forecast fetch(double latitude, double longitude, ZoneId zoneId) {
        URI uri = forecastUri(latitude, longitude, zoneId);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "StackChan-Companion/1")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw unavailable(WorkdayWeatherFailureCode.REQUEST_FAILED, "Open-Meteo request failed");
                }
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw unavailable(WorkdayWeatherFailureCode.RESPONSE_TOO_LARGE, "Open-Meteo response too large");
                }
                return parse(bytes, zoneId);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(WorkdayWeatherFailureCode.REQUEST_FAILED, "Open-Meteo request interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw unavailable(WorkdayWeatherFailureCode.REQUEST_FAILED, "Open-Meteo request failed", exception);
        }
    }

    private URI forecastUri(double latitude, double longitude, ZoneId zoneId) {
        String query = "latitude=" + coordinate(latitude)
                + "&longitude=" + coordinate(longitude)
                + "&current=" + CURRENT_FIELDS
                + "&daily=" + DAILY_FIELDS
                + "&timezone=" + URLEncoder.encode(zoneId.getId(), StandardCharsets.UTF_8)
                + "&forecast_days=2"
                + "&temperature_unit=celsius"
                + "&precipitation_unit=mm"
                + "&timeformat=iso8601";
        return URI.create(forecastEndpoint + "?" + query);
    }

    private String coordinate(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private Forecast parse(byte[] body, ZoneId requestedZone) {
        try {
            ForecastResponse response = objectMapper.readValue(body, ForecastResponse.class);
            if (response == null || !requestedZone.getId().equals(response.timezone())
                    || response.current() == null || response.daily() == null) {
                throw invalidResponse();
            }
            CurrentResponse current = response.current();
            CurrentWeather currentWeather = new CurrentWeather(
                    localInstant(current.time(), requestedZone),
                    finite(current.temperature(), -100, 70),
                    finite(current.apparentTemperature(), -120, 80),
                    finite(current.precipitation(), 0, 2000),
                    weatherCode(current.weatherCode())
            );
            DailyResponse daily = response.daily();
            int count = consistentDailyCount(daily);
            if (count < 1 || count > 2) {
                throw invalidResponse();
            }
            List<DailyForecast> forecasts = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                double temperatureMax = finite(daily.temperatureMax().get(index), -100, 70);
                double temperatureMin = finite(daily.temperatureMin().get(index), -100, 70);
                double apparentMax = finite(daily.apparentTemperatureMax().get(index), -120, 80);
                double apparentMin = finite(daily.apparentTemperatureMin().get(index), -120, 80);
                if (temperatureMin > temperatureMax || apparentMin > apparentMax) {
                    throw invalidResponse();
                }
                Integer probability = daily.precipitationProbabilityMax().get(index);
                if (probability == null || probability < 0 || probability > 100) {
                    throw invalidResponse();
                }
                forecasts.add(new DailyForecast(
                        LocalDate.parse(daily.time().get(index)),
                        weatherCode(daily.weatherCode().get(index)),
                        temperatureMax,
                        temperatureMin,
                        apparentMax,
                        apparentMin,
                        probability,
                        finite(daily.precipitationSum().get(index), 0, 5000)
                ));
            }
            return new Forecast(currentWeather, List.copyOf(forecasts));
        } catch (WorkdayWeatherUnavailableException exception) {
            throw exception;
        } catch (IOException | DateTimeParseException | NullPointerException | IndexOutOfBoundsException exception) {
            throw unavailable(WorkdayWeatherFailureCode.INVALID_RESPONSE, "Open-Meteo response invalid", exception);
        }
    }

    private int consistentDailyCount(DailyResponse daily) {
        if (daily.time() == null || daily.weatherCode() == null
                || daily.temperatureMax() == null || daily.temperatureMin() == null
                || daily.apparentTemperatureMax() == null || daily.apparentTemperatureMin() == null
                || daily.precipitationProbabilityMax() == null || daily.precipitationSum() == null) {
            throw invalidResponse();
        }
        int count = daily.time().size();
        if (daily.weatherCode().size() != count || daily.temperatureMax().size() != count
                || daily.temperatureMin().size() != count || daily.apparentTemperatureMax().size() != count
                || daily.apparentTemperatureMin().size() != count
                || daily.precipitationProbabilityMax().size() != count
                || daily.precipitationSum().size() != count) {
            throw invalidResponse();
        }
        return count;
    }

    private Instant localInstant(String value, ZoneId zoneId) {
        return LocalDateTime.parse(value).atZone(zoneId).toInstant();
    }

    private double finite(Double value, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw invalidResponse();
        }
        return value;
    }

    private int weatherCode(Integer code) {
        if (code == null || !WEATHER_CODES.contains(code)) {
            throw invalidResponse();
        }
        return code;
    }

    private WorkdayWeatherUnavailableException invalidResponse() {
        return unavailable(WorkdayWeatherFailureCode.INVALID_RESPONSE, "Open-Meteo response invalid");
    }

    private WorkdayWeatherUnavailableException unavailable(WorkdayWeatherFailureCode code, String message) {
        return new WorkdayWeatherUnavailableException(code, message);
    }

    private WorkdayWeatherUnavailableException unavailable(
            WorkdayWeatherFailureCode code,
            String message,
            Throwable cause
    ) {
        return new WorkdayWeatherUnavailableException(code, message, cause);
    }

    public record Forecast(CurrentWeather current, List<DailyForecast> daily) { }

    public record CurrentWeather(
            Instant observedAt,
            double temperature,
            double apparentTemperature,
            double precipitation,
            int weatherCode
    ) { }

    public record DailyForecast(
            LocalDate date,
            int weatherCode,
            double temperatureMax,
            double temperatureMin,
            double apparentTemperatureMax,
            double apparentTemperatureMin,
            int precipitationProbabilityMax,
            double precipitationSum
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastResponse(
            String timezone,
            CurrentResponse current,
            DailyResponse daily
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CurrentResponse(
            String time,
            @JsonProperty("temperature_2m") Double temperature,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            Double precipitation,
            @JsonProperty("weather_code") Integer weatherCode
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DailyResponse(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @JsonProperty("apparent_temperature_max") List<Double> apparentTemperatureMax,
            @JsonProperty("apparent_temperature_min") List<Double> apparentTemperatureMin,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum
    ) { }
}
