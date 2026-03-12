package ghrir.digikarte.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MenuItemDto {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private String section;
    private Integer sortOrder;
}
