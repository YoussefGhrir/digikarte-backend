package ghrir.digikarte.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long userId;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;

    // Peut être null si l'utilisateur n'a pas d'organisation.
    private String country;

    private int organizationsCount;
    private long menusCount;

    /**
     * Valeurs possibles (string) :
     * ACTIVE, TRIALING, EXPIRED, CANCELLED, NO_SUBSCRIPTION, ERROR
     */
    private String subscriptionStatus;
    private String subscriptionPlan;

    /**
     * Si true => accès direct au dashboard sans abonnement (paywall désactivé).
     */
    private boolean subscriptionBypass;

    /**
     * Base64 JPEG, ou null si aucun fichier n'est enregistré.
     * (Utilisé uniquement côté admin pour afficher un avatar.)
     */
    private String profilePhotoBase64;
}

