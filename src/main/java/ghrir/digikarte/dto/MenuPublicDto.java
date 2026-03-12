package ghrir.digikarte.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuPublicDto {
    private String title;
    private String description;
    private String organizationName;
    /** Logo de l'organisation en Base64 (JPEG), pour affichage en tête du menu public. */
    private String organizationLogoBase64;
    private List<MenuItemDto> items = new ArrayList<>();
}
