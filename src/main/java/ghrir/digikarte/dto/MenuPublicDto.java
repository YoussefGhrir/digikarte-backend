package ghrir.digikarte.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuPublicDto {
    private String title;
    private String description;
    private String organizationName;
    /** Slogan du restaurant (affiché sous le nom dans le menu public). */
    private String organizationSlogan;
    /** Logo de l'organisation en Base64 (JPEG), pour affichage en tête du menu public. */
    private String organizationLogoBase64;
    /** Adresse formatée (rue, CP ville, pays) – pour footer menu public et conformité café/resto Allemagne. */
    private String organizationAddress;
    private String organizationPhone;
    private String organizationEmail;
    /** Template d'affichage: classic, cafe, bistro, minimal, cards, elegant */
    private String displayTemplate;
    /** Unité des prix (devise) : EUR, USD, TND, GBP, CHF, etc. */
    private String priceCurrency;
    private List<MenuItemDto> items = new ArrayList<>();
}
