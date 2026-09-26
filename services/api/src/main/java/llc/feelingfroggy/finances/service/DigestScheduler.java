package llc.feelingfroggy.finances.service;

import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Pushes each person's digest once a day, at their chosen time, when {@code finances.digest.scheduled}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "finances.digest.scheduled", havingValue = "true")
public class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final DigestService digest;
    private final AppUserRepository users;

    public DigestScheduler(DigestService digest, AppUserRepository users) {
        this.digest = digest;
        this.users = users;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT90S")
    public void tick() {
        for (var user : users.findAll()) {
            try {
                if (digest.dueForAutomaticSend(user.getId())) {
                    var run = digest.send(user.getId(), false);
                    log.info("digest for {}: {} item(s), sent={}", run.forDate(), run.items(), run.sent());
                }
            } catch (RuntimeException e) {
                log.warn("digest failed: {}", e.getMessage());
            }
        }
    }
}
