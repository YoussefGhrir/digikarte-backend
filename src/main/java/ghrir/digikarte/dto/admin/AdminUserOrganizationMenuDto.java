package ghrir.digikarte.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserOrganizationMenuDto {
    private Long menuId;
    private String title;
    /** Slug URL publique (/menu/{slug}), peut être null pour d'anciens enregistrements. */
    private String slug;
}
