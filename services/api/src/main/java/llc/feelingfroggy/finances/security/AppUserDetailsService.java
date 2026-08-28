package llc.feelingfroggy.finances.security;

import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up the password-login first factor.
 *
 * <p>The username is the email address, matched case-insensitively so it agrees with the
 * {@code lower(email)} unique index — otherwise "Allen@..." and "allen@..." would be one account at
 * signup and two at login.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final LoginAttemptService attempts;

    public AppUserDetailsService(AppUserRepository users, LoginAttemptService attempts) {
        this.users = users;
        this.attempts = attempts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByEmailIgnoreCase(username)
            // Deliberately vague: the message must not reveal whether the account exists.
            .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        if (user.getPasswordHash() == null) {
            // A passkey-only account cannot authenticate down this path.
            throw new UsernameNotFoundException("Bad credentials");
        }

        // Reported as a flag rather than thrown from here. DaoAuthenticationProvider wraps
        // anything this method throws — other than UsernameNotFoundException — in an
        // InternalAuthenticationServiceException, so a LockedException raised here reached the
        // caller disguised as a server fault and was answered as bad credentials. Setting the flag
        // lets Spring's own pre-authentication check raise it, and that runs before the password is
        // compared: a locked account still cannot be brute-forced, and the lock still cannot be
        // probed by timing the comparison.
        return User.withUsername(user.getEmail())
            .password(user.getPasswordHash())
            .disabled(!user.isActive())
            .accountLocked(attempts.isLocked(username))
            .authorities("ROLE_USER")
            .build();
    }
}
