package llc.feelingfroggy.finances.ai;

import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Thin client for the Python AI service.
 *
 * <p>The AI service is stateless and advisory: it never writes to the database and its output is
 * always re-validated by this service before anything is persisted. See docs/ARCHITECTURE.md.
 */
@Component
public class AiServiceClient {

    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(AiServiceClient.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    /** A parser's verdict is a sentence or two; anything longer was not written to be shown. */
    public static final int MAX_DETAIL_LENGTH = 300;

    private final RestClient restClient;

    public AiServiceClient(AiServiceProperties properties, RestClient.Builder builder) {
        // HTTP/1.1 is pinned deliberately. The JDK's HttpClient defaults to HTTP/2 and, on a
        // cleartext connection, opens with an h2c upgrade attempt. Uvicorn speaks HTTP/1.1 only:
        // it logs "Unsupported upgrade request", the request framing is then mangled, and a
        // multipart upload arrives with no parts at all — FastAPI reports the file field as simply
        // missing. Health checks survived it (no body to mangle), so this only surfaced once
        // statement upload existed.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();

        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // The configured timeout must reach the request factory. Without it finances.ai.timeout is
        // dead configuration and a hung AI service blocks a request thread indefinitely — statement
        // parsing and categorization are the slow paths.
        requestFactory.setReadTimeout(properties.timeout());

        this.restClient = builder
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    /**
     * Parses an uploaded CSV statement into normalized transaction candidates.
     *
     * <p>The file is sent as multipart, exactly as the service expects. Nothing is persisted on the
     * far side — docs/ARCHITECTURE.md makes the AI service stateless, and docs/SECURITY.md makes
     * statement retention this service's responsibility.
     *
     * <p>{@code accountRef} is an opaque handle, not an account number: it only has to be stable so
     * the parser's dedupe keys are stable. The account id serves, and it tells the AI service
     * nothing about the account.
     *
     * @throws AiServiceException if the service is unreachable or rejects the file
     */
    public ParseResult parseCsv(byte[] content, String filename, String accountRef) {
        return parse("/parse/csv", content, filename, accountRef, "statement.csv");
    }

    /**
     * Parses an uploaded OFX or QFX statement.
     *
     * <p>Richer than CSV: OFX carries the institution's own transaction ids and a closing balance,
     * so the result supports both provider-id dedupe and a reconciliation checkpoint.
     */
    public ParseResult parseOfx(byte[] content, String filename, String accountRef) {
        return parse("/parse/ofx", content, filename, accountRef, "statement.ofx");
    }

    /**
     * Parses a brokerage positions export into a holdings snapshot.
     *
     * <p>No {@code account_ref}: a positions file names an account on every row, so there is
     * nothing for the caller to nominate. Unlike the statement parsers this returns no
     * transactions — a positions file records no money movement at all.
     */
    public PositionsResult parsePositions(byte[] content, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename == null ? "positions.csv" : filename;
            }
        });

        try {
            PositionsResult result = restClient.post()
                .uri("/parse/positions")
                // Content-Type deliberately unset — see the note in parse() below.
                .body(form)
                .retrieve()
                .body(PositionsResult.class);

            if (result == null) {
                throw new AiServiceException("The parser returned an empty response.");
            }
            return result;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw refusal(e, "The positions file could not be parsed.");
        }
    }

    private ParseResult parse(String path, byte[] content, String filename, String accountRef,
                              String fallbackFilename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                // Multipart requires a filename part; without one the service sees no upload.
                return filename == null ? fallbackFilename : filename;
            }
        });

        try {
            ParseResult result = restClient.post()
                .uri(uriBuilder -> uriBuilder.path(path)
                    .queryParam("account_ref", accountRef)
                    .build())
                // Content-Type is deliberately NOT set here. Spring's form converter generates the
                // multipart boundary and writes the full header itself; setting
                // "multipart/form-data" by hand pins it *without* a boundary, and the receiver then
                // finds no parts at all — FastAPI reports the file field as simply missing.
                .body(form)
                .retrieve()
                .body(ParseResult.class);

            if (result == null) {
                throw new AiServiceException("The parser returned an empty response.");
            }
            return result;
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw refusal(e, "The statement could not be parsed.");
        }
    }

    /**
     * What to tell the caller when the parser says no.
     *
     * <p>A 422 is the parser's own verdict on the file — "No parser matches this file. Known
     * formats: …", "Not a positions export: missing Symbol …" — written for a person, and it is
     * passed through as the message. Every other failure gets the fixed sentence: the service
     * being down, a 500, a body that is not the expected shape, or a validation list rather than
     * a sentence. Whatever text those carry was not written to be shown, and can quote the file.
     *
     * <p>The fixed sentence used to be the answer for everything. The parser's explanations
     * reached nobody, and the one HTTP test that claimed otherwise mocked the sentence it asserted.
     *
     * <p>The raw body is logged at DEBUG only: docs/SECURITY.md keeps statement content out of
     * INFO-level logs, and a rejected body can quote the file.
     */
    private AiServiceException refusal(Exception e, String fallback) {
        if (e instanceof RestClientResponseException response) {
            log.debug("Parser rejected the upload: {} {}", response.getStatusCode(),
                response.getResponseBodyAsString());
            if (response.getStatusCode().value() == 422) {
                String detail = detailOf(response.getResponseBodyAsString());
                if (detail != null) {
                    return new AiServiceException(detail, e);
                }
            }
        }
        return new AiServiceException(fallback, e);
    }

    /** FastAPI's {@code {"detail": "…"}} when it is a sentence; null for anything else. */
    public static String detailOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode detail = JSON.readTree(body).path("detail");
            if (!detail.isString()) {
                return null;
            }
            String text = detail.asString().strip();
            if (text.isEmpty()) {
                return null;
            }
            return text.length() > MAX_DETAIL_LENGTH
                ? text.substring(0, MAX_DETAIL_LENGTH - 1) + "…"
                : text;
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    /** Thrown when the parser is unreachable or refuses a file. */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) {
            super(message);
        }

        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Returns the AI service's reported health, or {@code "unreachable"} if it is down. */
    public String health() {
        try {
            HealthResponse response = restClient.get()
                .uri("/health")
                .retrieve()
                .body(HealthResponse.class);
            return response == null ? "unknown" : response.status();
        } catch (Exception e) {
            return "unreachable";
        }
    }

    public record HealthResponse(String status, String service, String version) {}
}
