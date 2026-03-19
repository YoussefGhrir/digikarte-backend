package ghrir.digikarte.dto.admin;

import lombok.Data;

@Data
public class AdminUpdateUserRequest {
    private String nom;
    private String prenom;
    private String telephone;

    /**
     * Si true => paywall désactivé (accès direct).
     */
    private Boolean subscriptionBypass;

    /**
     * Si true, l'utilisateur a accès admin (hors super admin).
     */
    private Boolean admin;
}

