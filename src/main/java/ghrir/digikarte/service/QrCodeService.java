package ghrir.digikarte.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Génère une image QR code en PNG (format par défaut).
     * @param slug slug du menu (URL relative sera /menu/[slug])
     * @param size taille en pixels (carré)
     * @return bytes PNG
     */
    public byte[] generatePng(String slug, int size) throws Exception {
        String url = frontendUrl + "/menu/" + slug;
        return generateQrPng(url, size, null);
    }

    /**
     * Génère un QR avec niveau de correction d'erreur (pour différents "modèles" côté backend).
     * L=7%, M=15%, Q=25%, H=30%
     */
    public byte[] generatePngWithErrorLevel(String slug, int size, String errorLevel) throws Exception {
        String url = frontendUrl + "/menu/" + slug;
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

    /** URL publique du menu (slug immutable côté entity → lien fixe à vie, adapté à l’impression QR). */
    public String getMenuPublicUrl(String slug) {
        return frontendUrl + "/menu/" + slug;
    }
}
