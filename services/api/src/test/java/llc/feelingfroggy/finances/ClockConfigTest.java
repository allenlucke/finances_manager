package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import llc.feelingfroggy.finances.config.ClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The application clock")
class ClockConfigTest {

    @Test
    @DisplayName("runs in the configured zone, not the container's")
    void usesTheConfiguredZone() {
        assertThat(new ClockConfig().clock("America/Chicago").getZone())
            .isEqualTo(ZoneId.of("America/Chicago"));
        assertThat(new ClockConfig().clock(" Europe/London ").getZone())
            .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    @DisplayName("a misspelled zone fails at startup rather than quietly meaning UTC")
    void refusesAnUnknownZone() {
        assertThatThrownBy(() -> new ClockConfig().clock("America/Kansas_City"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_TIMEZONE");
    }
}
