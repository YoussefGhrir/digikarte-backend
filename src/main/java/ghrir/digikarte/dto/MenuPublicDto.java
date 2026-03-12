package ghrir.digikarte.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuPublicDto {
    private String title;
    private String description;
    private String organizationName;
    private List<MenuItemDto> items = new ArrayList<>();
}
