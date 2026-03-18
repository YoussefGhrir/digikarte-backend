package ghrir.digikarte.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String nom;

    @NotBlank
    @Column(nullable = false)
    private String prenom;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String telephone;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Basic(fetch = LAZY)
    @Column(name = "profile_photo")
    private byte[] profilePhoto;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Organization> organizations = new ArrayList<>();

    /**
     * Identifiant du client Stripe associé à cet utilisateur (mode test ou live).
     * Peut être null si l'utilisateur n'a pas encore démarré de flux Stripe.
     */
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    /**
     * Identifiant de l'abonnement Stripe courant de l'utilisateur.
     * Utilisé pour récupérer l'état d'abonnement et les périodes de facturation.
     */
    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;
}
