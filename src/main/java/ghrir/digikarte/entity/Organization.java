package ghrir.digikarte.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /** Slogan du restaurant / café (affiché sur le menu public). */
    @Column(length = 255)
    private String slogan;

    /** Adresse (rue et numéro) – pour café/resto, menu public et conformité Allemagne. */
    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    /** Pays (ex. Deutschland / Allemagne). */
    @Column(length = 100)
    private String country;

    /** Téléphone – affiché dans le menu public et footer. */
    @Column(length = 50)
    private String phone;

    /** Email de contact (optionnel). */
    @Column(length = 255)
    private String email;

    /** Logo de l'organisation (JPEG), stocké en base pour affichage dans le menu public. */
    @Lob
    @Column(name = "logo")
    private byte[] logo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Menu> menus = new ArrayList<>();
}
