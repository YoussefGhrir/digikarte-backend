package ghrir.digikarte.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuDto {
    private Long id;
    private String title;
    private String description;
    private String slug;
    private Long organizationId;
    /** Template d'affichage: classic, cafe, bistro, minimal, cards, elegant */
    private String displayTemplate;
    /** Unité des prix (devise) : EUR, USD, TND, GBP, CHF, etc. */
    private String priceCurrency;
    private List<MenuItemDto> items = new ArrayList<>();
}
