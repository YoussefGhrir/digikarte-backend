package ghrir.digikarte.dto;

import lombok.Data;

@Data
public class OrganizationDto {
    private Long id;
    private String name;
    private String description;
    /** Logo encodé en Base64 (JPEG), pour affichage et menu public. */
    private String organizationLogoBase64;
}
