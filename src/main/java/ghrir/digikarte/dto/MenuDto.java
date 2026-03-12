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
    private List<MenuItemDto> items = new ArrayList<>();
}
