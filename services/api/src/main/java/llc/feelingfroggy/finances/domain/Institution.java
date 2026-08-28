package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A financial provider: a bank, a brokerage, Feeling Froggy's own books, or manual entry. */
@Entity
@Table(name = "institution")
public class Institution extends UserOwned {

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Convert(converter = InstitutionKind.Conv.class)
    @Column(name = "kind", nullable = false, length = 30)
    private InstitutionKind kind;

    protected Institution() {
    }

    public Institution(Long userId, String name, InstitutionKind kind) {
        super(userId);
        this.name = name;
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InstitutionKind getKind() {
        return kind;
    }

    public void setKind(InstitutionKind kind) {
        this.kind = kind;
    }
}
