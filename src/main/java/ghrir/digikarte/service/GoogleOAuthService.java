package ghrir.digikarte.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    @Value("${GOOGLE_CLIENT_ID}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${GOOGLE_REDIRECT_URI}")
    private String redirectUri;

    private final UserRepository userRepository;
    private final AuthService authService;
    private final ProfilePhotoService profilePhotoService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String buildGoogleAuthUrl(String lang, String source) {
        String scope = "openid%20email%20profile";
        String encodedRedirect = java.net.URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String safeLang = (lang != null && !lang.isBlank()) ? lang : "de";
        String safeSource = (source != null && !source.isBlank()) ? source : "login";
        String state = safeLang + ":" + safeSource;
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + state
                + "&access_type=online"
                + "&include_granted_scopes=true";
    }

    public AuthResponse handleCallback(String code, boolean registerFlow) throws Exception {
        String tokenBody =
                "code=" + encode(code)
                        + "&client_id=" + encode(clientId)
                        + "&client_secret=" + encode(clientSecret)
                        + "&redirect_uri=" + encode(redirectUri)
                        + "&grant_type=authorization_code";

        HttpRequest tokenRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                .build();

        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        if (tokenResponse.statusCode() != 200) {
            throw new IllegalStateException("Google token exchange failed: " + tokenResponse.body());
        }

        JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
        String accessToken = tokenJson.get("access_token").asText();

        HttpRequest userInfoRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/oauth2/v2/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> userInfoResponse = httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
        if (userInfoResponse.statusCode() != 200) {
            throw new IllegalStateException("Google userinfo failed: " + userInfoResponse.body());
        }

        JsonNode profile = objectMapper.readTree(userInfoResponse.body());
        String email = profile.get("email").asText();
        String givenName = profile.hasNonNull("given_name") ? profile.get("given_name").asText() : "";
        String familyName = profile.hasNonNull("family_name") ? profile.get("family_name").asText() : "";
        String pictureUrl = profile.hasNonNull("picture") ? profile.get("picture").asText() : null;

        Optional<User> existingOpt = userRepository.findByEmail(email);
        User user;
        if (existingOpt.isPresent()) {
            // Si on vient du flux "register" et que l'email existe déjà,
            // on ne crée pas / ne connecte pas: on laisse le contrôleur gérer l'erreur.
            if (registerFlow) {
                throw new IllegalStateException("EMAIL_ALREADY_EXISTS:" + email);
            }
            user = existingOpt.get();
            if (user.getPrenom() == null || user.getPrenom().isBlank()) user.setPrenom(givenName);
            if (user.getNom() == null || user.getNom().isBlank()) user.setNom(familyName);
        } else {
            user = User.builder()
                    .email(email)
                    .prenom(givenName)
                    .nom(familyName)
                    .telephone("N/A")
                    .password(java.util.UUID.randomUUID().toString())
                    .build();
        }

        boolean isNewUser = existingOpt.isEmpty();
        if (pictureUrl != null && !pictureUrl.isBlank()) {
            boolean shouldApplyGooglePhoto = isNewUser
                    || user.getProfilePhoto() == null
                    || user.getProfilePhoto().length == 0;
            if (shouldApplyGooglePhoto) {
                fetchAndApplyGoogleProfilePhoto(user, pictureUrl);
            }
        }

        user = userRepository.save(user);

        return authService.generateAuthResponseForUser(user);
    }

    /**
     * Télécharge l'avatar Google (userinfo "picture") et le stocke comme photo de profil DigiKarte.
     * Ne remplace pas une photo déjà définie pour un compte existant (upload manuel).
     */
    private void fetchAndApplyGoogleProfilePhoto(User user, String pictureUrl) {
        if (!isAllowedGoogleAvatarUrl(pictureUrl)) {
            log.warn("Ignored Google profile picture URL with unexpected host/scheme");
            return;
        }
        try {
            HttpRequest picRequest = HttpRequest.newBuilder()
                    .uri(URI.create(pictureUrl))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> picResponse =
                    httpClient.send(picRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (picResponse.statusCode() != 200) {
                log.warn("Google profile picture HTTP {}", picResponse.statusCode());
                return;
            }
            byte[] body = picResponse.body();
            if (body == null || body.length == 0 || body.length > ProfilePhotoService.getMaxInputBytes()) {
                return;
            }
            String contentType = picResponse.headers().firstValue("Content-Type").orElse("image/jpeg");
            byte[] processed = profilePhotoService.processProfilePhoto(new ByteArrayInputStream(body), contentType);
            user.setProfilePhoto(processed);
        } catch (Exception e) {
            log.warn("Could not apply Google profile photo for user {}", user.getEmail(), e);
        }
    }

    private static boolean isAllowedGoogleAvatarUrl(String url) {
        try {
            URI u = URI.create(url);
            if (!"https".equalsIgnoreCase(u.getScheme())) {
                return false;
            }
            String host = u.getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase();
            return h.endsWith("googleusercontent.com") || h.endsWith("ggpht.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String encode(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}

