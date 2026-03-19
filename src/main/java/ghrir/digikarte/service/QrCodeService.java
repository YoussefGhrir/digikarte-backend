package ghrir.digikarte.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import ghrir.digikarte.util.FrontendUrlUtil;
import ghrir.digikarte.util.RouteLocaleUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    @Value("${frontend.url:https://www.digi-karte.com}")
    private String frontendUrl;

    private String menuPublicUrl(String slug, String routeLocale) {
        String loc = RouteLocaleUtil.sanitize(routeLocale);
        return FrontendUrlUtil.join(
                frontendUrl,
                RouteLocaleUtil.prefixedPath(loc, "/menu/" + slug)
        );
    }

    /**
     * @param routeLocale langue courante UI (de/fr/en), jamais déduite du pays côté backend.
     */
    public byte[] generatePng(String slug, String routeLocale, int size) throws Exception {
        return generateQrPng(menuPublicUrl(slug, routeLocale), size, null);
    }

    /**
     * @param routeLocale langue courante UI (de/fr/en).
     */
    public byte[] generatePngWithErrorLevel(String slug, String routeLocale, int size, String errorLevel) throws Exception {
        String url = menuPublicUrl(slug, routeLocale);
        ErrorCorrectionLevel level = parseErrorLevel(errorLevel);
        return generateQrPng(url, size, level);
    }

    private byte[] generateQrPng(String content, int size, ErrorCorrectionLevel level) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        if (level != null) {
            hints.put(EncodeHintType.ERROR_CORRECTION, level);
        } else {
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        }
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }

    private ErrorCorrectionLevel parseErrorLevel(String errorLevel) {
        if (errorLevel == null) return ErrorCorrectionLevel.M;
        return switch (errorLevel.toUpperCase()) {
            case "L" -> ErrorCorrectionLevel.L;
            case "Q" -> ErrorCorrectionLevel.Q;
            case "H" -> ErrorCorrectionLevel.H;
            default -> ErrorCorrectionLevel.M;
        };
    }

    /** URL publique du menu avec préfixe /{locale}/ selon la langue courante. */
    public String getMenuPublicUrl(String slug, String routeLocale) {
        return menuPublicUrl(slug, routeLocale);
    }
}
