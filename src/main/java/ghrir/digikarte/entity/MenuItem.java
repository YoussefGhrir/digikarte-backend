package ghrir.digikarte.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Entity
@Table(
        name = "menu_items",
        indexes = {
                @Index(name = "idx_menu_items_menu_id", columnList = "menu_id"),
                @Index(name = "idx_menu_items_parent_item_id", columnList = "parent_item_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 255)
    private String section;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** Si non null : sous-produit rattaché au plat parent (même section). */
    @Column(name = "parent_item_id")
    private Long parentItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) // supprime les items si le menu est supprimé
    private Menu menu;
}
