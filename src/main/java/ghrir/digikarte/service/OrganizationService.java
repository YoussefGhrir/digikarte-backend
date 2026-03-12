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
    public OrganizationDto create(Long userId, String name, String description) {
        User owner = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        Organization org = Organization.builder()
                .name(name)
                .description(description)
                .owner(owner)
                .build();
        org = organizationRepository.save(org);
        return toDto(org);
    }

    @Transactional
    public OrganizationDto update(Long id, Long userId, String name, String description) {
        Organization org = organizationRepository.findById(id).orElseThrow(() -> new RuntimeException("Organisation non trouvée"));
        if (!org.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Non autorisé");
        }
        if (name != null) org.setName(name);
        if (description != null) org.setDescription(description);
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
        dto.setOrganizationLogoBase64(organizationPhotoService.toBase64(org.getLogo()));
        return dto;
    }
}
