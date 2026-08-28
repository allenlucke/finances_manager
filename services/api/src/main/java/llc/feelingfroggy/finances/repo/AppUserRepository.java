package llc.feelingfroggy.finances.repo;

import java.util.Optional;
import llc.feelingfroggy.finances.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Case-insensitive, matching the {@code lower(email)} unique index. */
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmailIgnoreCase(@Param("email") String email);
}
