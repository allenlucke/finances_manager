package llc.feelingfroggy.finances.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * A browser-shaped HTTP client for integration tests.
 *
 * <p>Spring Boot 4 removed {@code TestRestTemplate}. Rather than adopt a fluent assertion client,
 * this uses the JDK's {@link HttpClient}, whose {@link CookieManager} stores the session cookie the
 * way a browser does — which is the behaviour under test, since D-12 chose cookie sessions over
 * bearer tokens.
 *
 * <p>Jackson 3 ({@code tools.jackson}), not Jackson 2 — Spring Boot 4 moved to it, and the old
 * {@code com.fasterxml.jackson.databind} package is not on the classpath.
 *
 * <p>It also mirrors what Angular does with CSRF: read the {@code XSRF-TOKEN} cookie and echo it
 * back in {@code X-XSRF-TOKEN}. A test that skipped that would pass while the real SPA got 403s.
 */
public final class ApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final CookieManager cookies;
    private final String baseUrl;
    private String bearer;
    private final Map<String, String> extraHeaders = new java.util.LinkedHashMap<>();

    public ApiClient(int port) {
        this.cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.baseUrl = "http://localhost:" + port;
    }

    /** Clears cookies, so one test's session never leaks into the next. */
    public void reset() {
        cookies.getCookieStore().removeAll();
    }

    /**
     * Sends {@code Authorization: Bearer <token>} on every subsequent request (D-17).
     *
     * <p>Pair it with {@link #reset()} when testing token auth: with a session cookie still in the
     * jar the request would succeed either way, and the test would pass without proving anything.
     */
    public ApiClient bearer(String token) {
        this.bearer = token;
        return this;
    }

    /** An arbitrary header on every subsequent request — for asserting what the API ignores. */
    public ApiClient header(String name, String value) {
        extraHeaders.put(name, value);
        return this;
    }

    /** Fetches a CSRF token, which also seeds the session. Required before any write. */
    public void primeCsrf() {
        get("/api/v1/auth/csrf");
    }

    public Response get(String path) {
        return send(request(path).GET());
    }

    public Response postJson(String path, Object body) {
        return send(request(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(write(body))));
    }

    public Response putJson(String path, Object body) {
        return send(request(path)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(write(body))));
    }

    public Response delete(String path) {
        return send(request(path).DELETE());
    }

    /** JSON, because AuthController owns login rather than Spring Security's form filter. */
    public Response login(String username, String password) {
        return postJson("/api/v1/auth/login",
            Map.of("username", username, "password", password));
    }

    /** Sends the session cookie but deliberately omits the CSRF header, as a forged request would. */
    public Response postJsonWithoutCsrf(String path, Object body) {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(write(body)));
        return send(builder, false);
    }

    /**
     * Every request advertises {@code Accept: application/json}, exactly as Angular's HttpClient
     * does. This is not cosmetic: Spring Security content-negotiates its authentication entry
     * point, so a client that omits it is treated as a browser and gets a redirect to an HTML login
     * page instead of a status code.
     */
    private HttpRequest.Builder request(String path) {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Accept", "application/json");
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        extraHeaders.forEach(builder::header);
        return builder;
    }

    private Response send(HttpRequest.Builder builder) {
        return send(builder, true);
    }

    private Response send(HttpRequest.Builder builder, boolean withCsrf) {
        if (withCsrf) {
            csrfToken().ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> response =
                http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(),
                response.headers().firstValue("Location").orElse(null));
        } catch (Exception e) {
            throw new IllegalStateException("Request failed", e);
        }
    }

    private java.util.Optional<String> csrfToken() {
        return cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
            .map(HttpCookie::getValue)
            .findFirst();
    }

    /** The current session id, for asserting it changes when it should. */
    public java.util.Optional<String> sessionCookie() {
        return cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> "SESSION".equals(cookie.getName()))
            .map(HttpCookie::getValue)
            .findFirst();
    }

    public boolean hasSessionCookie() {
        return cookies.getCookieStore().getCookies().stream()
            .anyMatch(cookie -> "SESSION".equals(cookie.getName()));
    }

    private static String write(Object body) {
        try {
            return body instanceof String s ? s : JSON.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize " + body, e);
        }
    }

    /**
     * Status, raw body, and any {@code Location} — redirects are not followed, so a redirect is
     * observable rather than silently resolved into whatever it points at.
     */
    public record Response(int status, String body, String location) {

        public JsonNode json() {
            try {
                return JSON.readTree(body);
            } catch (Exception e) {
                throw new IllegalStateException("Body was not JSON: " + body, e);
            }
        }

        public long id() {
            return json().get("id").asLong();
        }

        public Map<String, Object> asMap() {
            try {
                return JSON.readValue(body, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Body was not a JSON object: " + body, e);
            }
        }
    }
}
