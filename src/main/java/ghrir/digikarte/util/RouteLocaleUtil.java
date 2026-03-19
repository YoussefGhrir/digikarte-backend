package ghrir.digikarte.util;

/**
 * Aligné sur le frontend : segments de langue dans l'URL (/de/, /fr/, /en/).
 * Le CORS du navigateur ne dépend pas du path, mais Stripe / Google OAuth / QR doivent
 * renvoyer des URLs cohérentes avec cette structure.
 */
public final class RouteLocaleUtil {

    private RouteLocaleUtil() {
    }

    /** Langue URL autorisée (identique au middleware Next.js). */
    public static String sanitize(String lang) {
        if (lang == null || lang.isBlank()) {
            return "de";
        }
        String l = lang.trim().toLowerCase();
        return switch (l) {
            case "fr", "en" -> l;
            default -> "de";
        };
    }

    /**
     * Préfixe un path applicatif avec /{locale}. Gère path + query éventuelle.
     * Ex. {@code prefixedPath("fr", "/login")} → {@code /fr/login};
     * {@code prefixedPath("de", "/dashboard/subscription?x=1")} → {@code /de/dashboard/subscription?x=1}.
     */
    public static String prefixedPath(String lang, String pathWithOptionalQuery) {
        String raw = pathWithOptionalQuery == null || pathWithOptionalQuery.isBlank()
                ? "/"
                : pathWithOptionalQuery.trim();
        if (!raw.startsWith("/")) {
            raw = "/" + raw;
        }
        int q = raw.indexOf('?');
        String pathOnly = q >= 0 ? raw.substring(0, q) : raw;
        String query = q >= 0 ? raw.substring(q) : "";
        return "/" + sanitize(lang) + pathOnly + query;
    }
}
