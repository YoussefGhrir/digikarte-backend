package ghrir.digikarte.service;

import ghrir.digikarte.dto.OrganizationDto;
import ghrir.digikarte.entity.Organization;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.OrganizationRepository;
import ghrir.digikarte.repository.UserRepository;
import ghrir.digikarte.service.OrganizationPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationPhotoService organizationPhotoService;

    @Transactional(readOnly = true)
    public List<OrganizationDto> findByOwnerId(Long userId) {
        return organizationRepository.findByOwnerId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrganizationDto create(Long userId, String name, String slogan,
                                  String addressLine1, String addressPostalCode, String addressCity,
                                  String country, String phone, String email) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Organization org = Organization.builder()
                .name(name)
                .description(null)
                .slogan(slogan)
                .addressLine1(addressLine1)
                .addressPostalCode(addressPostalCode)
                .addressCity(addressCity)
                .country(country)
                .phone(phone)
                .email(email)
                .owner(owner)
                .build();
        org = organizationRepository.save(org);
        return toDto(org);
    }

    @Transactional
    public OrganizationDto update(Long id, Long userId, String name, String slogan,
                                  String addressLine1, String addressPostalCode, String addressCity,
                                  String country, String phone, String email) {
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        if (name != null) org.setName(name);
        if (slogan != null) org.setSlogan(slogan);
        if (addressLine1 != null) org.setAddressLine1(addressLine1);
        if (addressPostalCode != null) org.setAddressPostalCode(addressPostalCode);
        if (addressCity != null) org.setAddressCity(addressCity);
        if (country != null) org.setCountry(country);
        if (phone != null) org.setPhone(phone);
        if (email != null) org.setEmail(email);
        return toDto(organizationRepository.save(org));
    }

    @Transactional
    public void delete(Long id, Long userId) {
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        organizationRepository.delete(org);
    }

    @Transactional
    public void updateLogo(Long id, Long userId, byte[] processedLogo) {
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        org.setLogo(processedLogo);
        organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public OrganizationDto getById(Long id, Long userId) {
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        return toDto(org);
    }

    private OrganizationDto toDto(Organization org) {
        OrganizationDto dto = new OrganizationDto();
        dto.setId(org.getId());
        dto.setName(org.getName());
        dto.setDescription(org.getDescription());
        dto.setSlogan(org.getSlogan());
        dto.setAddressLine1(org.getAddressLine1());
        dto.setAddressPostalCode(org.getAddressPostalCode());
        dto.setAddressCity(org.getAddressCity());
        dto.setCountry(org.getCountry());
        dto.setPhone(org.getPhone());
        dto.setEmail(org.getEmail());
        dto.setOrganizationLogoBase64(organizationPhotoService.toBase64(org.getLogo()));
        return dto;
    }
}
