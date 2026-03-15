package ghrir.digikarte.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menus")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String title;

    @Column(length = 2000)
    private String description;

    /**
     * Identifiant stable et unique du menu dans l’URL publique (/menu/{slug}).
     * Généré une seule fois à la création, jamais modifié — garantit que le QR code
     * reste valide à vie une fois imprimé (tables, stickers, etc.).
     */
    @Column(unique = true, nullable = false, length = 36, updatable = false)
    private String slug;

    /** Template d'affichage du menu public: classic, cafe, bistro, minimal, cards, elegant */
    @Column(name = "display_template", length = 32)
    private String displayTemplate;

    /** Unité des prix (devise) : EUR, USD, TND, GBP, CHF, etc. Défaut EUR pour site international. */
    @Column(name = "price_currency", length = 6)
    private String priceCurrency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<MenuItem> items = new ArrayList<>();

    /** Génère un slug unique et fixe à la première sauvegarde (pour QR imprimés). */
    @PrePersist
    public void generateSlug() {
        if (slug == null || slug.isBlank()) {
            slug = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }
}
