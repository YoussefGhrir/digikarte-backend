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

    @Column(unique = true, nullable = false, length = 36)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<MenuItem> items = new ArrayList<>();

    @PrePersist
    public void generateSlug() {
        if (slug == null || slug.isBlank()) {
            slug = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
    }
}
