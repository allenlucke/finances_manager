package llc.feelingfroggy.finances.service;

/** Where a fired alert goes. One implementation, ntfy; a blank URL is a notifier that says so. */
public interface Notifier {

    /** @throws NotificationFailed when the channel refused or could not be reached */
    void send(String title, String message);

    boolean configured();

    class NotificationFailed extends RuntimeException {
        public NotificationFailed(String message, Throwable cause) {
            super(message, cause);
        }

        public NotificationFailed(String message) {
            super(message);
        }
    }
}
