package ghrir.digikarte.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserOrganizationDto {
    private Long organizationId;
    private String name;
    private List<AdminUserOrganizationMenuDto> menus;
}
