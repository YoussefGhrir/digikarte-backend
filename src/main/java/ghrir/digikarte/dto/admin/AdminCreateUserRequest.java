package ghrir.digikarte.dto.admin;

import lombok.Data;

@Data
public class AdminCreateUserRequest {
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String password;

    /**
     * Si true => accès direct au dashboard sans abonnement.
     * Par défaut : true (compte créé “manuellement”).
     */
    private Boolean subscriptionBypass;

    /**
     * Si true, l'utilisateur obtient l'accès admin (non super admin).
     */
    private Boolean admin;
}

