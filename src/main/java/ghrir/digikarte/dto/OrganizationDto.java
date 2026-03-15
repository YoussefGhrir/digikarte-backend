package ghrir.digikarte.dto;

import lombok.Data;

@Data
public class OrganizationDto {
    private Long id;
    private String name;
    private String description;
    /** Slogan du restaurant (affiché sur le menu public). */
    private String slogan;
    /** Adresse (rue et numéro). */
    private String addressLine1;
    private String addressPostalCode;
    private String addressCity;
    /** Pays (ex. Deutschland). */
    private String country;
    private String phone;
    private String email;
    /** Logo encodé en Base64 (JPEG), pour affichage et menu public. */
    private String organizationLogoBase64;
}
