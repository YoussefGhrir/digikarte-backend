package ghrir.digikarte.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ghrir.digikarte.dto.AuthResponse;
import ghrir.digikarte.entity.User;
import ghrir.digikarte.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    @Value("${GOOGLE_CLIENT_ID}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${GOOGLE_REDIRECT_URI}")
    private String redirectUri;

    private final UserRepository userRepository;
    private final AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String buildGoogleAuthUrl(String lang) {
        String scope = "openid%20email%20profile";
        String encodedRedirect = java.net.URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String state = (lang != null && !lang.isBlank()) ? lang : "de";
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + state
                + "&access_type=online"
                + "&include_granted_scopes=true";
    }

    public AuthResponse handleCallback(String code) throws Exception {
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

        Optional<User> existingOpt = userRepository.findByEmail(email);
        User user;
        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (user.getPrenom() == null || user.getPrenom().isBlank()) user.setPrenom(givenName);
            if (user.getNom() == null || user.getNom().isBlank()) user.setNom(familyName);
        } else {
            user = User.builder()
                    .email(email)
                    .prenom(givenName)
                    .nom(familyName)
                    .telephone(null)
                    .build();
        }
        user = userRepository.save(user);

        return authService.generateAuthResponseForUser(user);
    }

    private static String encode(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}

