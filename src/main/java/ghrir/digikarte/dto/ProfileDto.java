package ghrir.digikarte.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDto {
    private String email;
    private String nom;
    private String prenom;
    private String telephone;
    /** Base64-encoded JPEG profile photo, or null if none. */
    private String profilePhotoBase64;

    /**
     * Si true, le dashboard n'applique pas le paywall abonnement (accès direct).
     */
    private boolean subscriptionBypass;
}
