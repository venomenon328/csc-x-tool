package de.venomenon.cscxtool.participant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

class UpdateParticipantRequest {

    @NotBlank(message = "Der Anzeigename darf nicht leer sein.")
    private String displayName;

    @NotBlank(message = "Das Land darf nicht leer sein.")
    private String countryCode;

    private Boolean active;
    private List<String> aliases;
    private boolean aliasesProvided;

    String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    String countryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    Boolean active() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    List<String> aliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
        this.aliasesProvided = true;
    }

    @JsonIgnore
    boolean aliasesProvided() {
        return aliasesProvided;
    }
}
