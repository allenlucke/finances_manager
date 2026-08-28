package llc.feelingfroggy.finances.ai;

import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
            // The parser's own explanation is logged at DEBUG only. It can quote the file, and
            // docs/SECURITY.md keeps transaction descriptions out of INFO-level logs — but without
            // it, diagnosing a rejected upload means guessing.
            if (e instanceof org.springframework.web.client.RestClientResponseException response) {
                log.debug("Parser rejected the upload: {} {}", response.getStatusCode(),
                    response.getResponseBodyAsString());
            }
            throw new AiServiceException("The statement could not be parsed.", e);
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
