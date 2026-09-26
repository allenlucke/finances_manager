package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The rules tier, in the Python service (M3a, D-15). Rows go over as the parser's own shape;
 * suggestions come back keyed by the dedupe key. This side maps a category <em>name</em> to the
 * person's categories and decides what to do with the confidence; the service never sees a
 * category id or writes anything.
 */
@Component
public class CategorizerClient {

    private final RestClient restClient;

    public CategorizerClient(AiServiceProperties properties, RestClient.Builder builder) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public List<WireSuggestion> categorize(List<Row> rows, String accountType) {
        if (rows.isEmpty()) {
            return List.of();
        }
        try {
            var response = restClient.post().uri("/categorize")
                .body(new Request(rows, accountType)).retrieve().body(Response.class);
            return response == null || response.suggestions() == null ? List.of() : response.suggestions();
        } catch (RestClientResponseException e) {
            String detail = AiServiceClient.detailOf(e.getResponseBodyAsString());
            throw new AiServiceClient.AiServiceException(
                detail == null ? "The categorizer refused the request." : detail, e);
        } catch (AiServiceClient.AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceClient.AiServiceException("The categorizer could not be reached.", e);
        }
    }

    /** The parser's row shape, as much of it as a rule can use. Amount as a string, never a float. */
    public record Row(@JsonProperty("transaction_date") LocalDate transactionDate, String description,
                      String merchant, String amount, String direction,
                      @JsonProperty("dedupe_key") String dedupeKey,
                      @JsonProperty("source_type") String sourceType,
                      @JsonProperty("is_probable_transfer") boolean isProbableTransfer,
                      @JsonProperty("is_probable_refund") boolean isProbableRefund) {
    }

    record Request(List<Row> transactions, @JsonProperty("account_type") String accountType) {
    }

    record Response(List<WireSuggestion> suggestions) {
    }

    /** Mirrors {@code finances_ai.models.CategorySuggestion}. {@code method} "none" means: nothing to say. */
    public record WireSuggestion(@JsonProperty("dedupe_key") String dedupeKey, String category,
                                 double confidence, String method, String rationale,
                                 @JsonProperty("is_transfer") boolean isTransfer) {
    }
}
