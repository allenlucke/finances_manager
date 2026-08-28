package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One configured link to an institution, with its own sync state. Named {@code SourceConnection}
 * rather than {@code Connection} so it is never confused with {@link java.sql.Connection}.
 *
 * <p>This is the persistent side of the {@code AccountConnector} port from D-14; file import is
 * simply the first {@link ConnectorType}.
 *
 * <p><strong>Credentials never live here.</strong> {@code credentialRef} is an opaque pointer into
 * whatever secret store is chosen before the first real token is stored — not a token, not a key.
 * See docs/SECURITY.md.
 */
@Entity
@Table(name = "connection")
public class SourceConnection extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Convert(converter = ConnectorType.Conv.class)
    @Column(name = "connector_type", nullable = false, length = 30)
    private ConnectorType connectorType;

    @Convert(converter = ConnectionStatus.Conv.class)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.ACTIVE;

    @Column(name = "credential_ref", length = 255)
    private String credentialRef;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error")
    private String lastError;

    protected SourceConnection() {
    }

    public SourceConnection(Long userId, Institution institution, String name, ConnectorType type) {
        super(userId);
        this.institution = institution;
        this.name = name;
        this.connectorType = type;
    }

    public Institution getInstitution() {
        return institution;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
