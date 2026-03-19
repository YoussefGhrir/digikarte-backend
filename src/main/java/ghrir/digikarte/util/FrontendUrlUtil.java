package ghrir.digikarte.util;

/** Joint {@code frontend.url} (sans slash final) et un chemin/query. */
public final class FrontendUrlUtil {

    private FrontendUrlUtil() {
    }

    public static String normalizeBase(String base) {
        if (base == null) {
            return "";
        }
        String b = base.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    public static String join(String base, String pathAndQuery) {
        String b = normalizeBase(base);
        if (pathAndQuery == null || pathAndQuery.isBlank()) {
            return b;
        }
        String p = pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery;
        return b + p;
    }
}
