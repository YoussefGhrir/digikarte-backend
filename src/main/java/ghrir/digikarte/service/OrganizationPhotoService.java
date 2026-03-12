package ghrir.digikarte.service;

import ghrir.digikarte.exception.ImageTooLargeException;
import ghrir.digikarte.exception.InvalidImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Validation et compression du logo d'organisation.
 * Accepte JPEG, PNG, WebP, GIF. Redimensionne si nécessaire (côté max 800px),
 * compresse en JPEG pour stockage fiable en base (affichage menu public).
 * Limites volontairement hautes pour accepter un maximum de photos.
 */
@Service
@Slf4j
public class OrganizationPhotoService {

    public static final int MAX_INPUT_BYTES = 15 * 1024 * 1024; // 15 MB
    private static final int MAX_LONGEST_SIDE_PX = 800;
    private static final int MAX_OUTPUT_BYTES = 2 * 1024 * 1024;   // 2 MB après compression

    private static final int MAX_DIMENSION_PX = 4000;

    /**
     * Valide, redimensionne si besoin et compresse l'image en JPEG.
     * @return octets JPEG (sans base64)
     */
    public byte[] processOrganizationLogo(InputStream inputStream, String contentType) {
        try {
            return processOrganizationLogoInternal(inputStream, contentType);
        } catch (InvalidImageException | ImageTooLargeException e) {
            throw e;
        } catch (Throwable t) {
            log.warn("Organization logo processing failed: {} - {}", t.getClass().getSimpleName(), t.getMessage(), t);
            throw new InvalidImageException("Could not process this image. Try saving it as JPEG or use a simpler image.");
        }
    }

    private byte[] processOrganizationLogoInternal(InputStream inputStream, String contentType) throws IOException {
        if (inputStream == null) {
            throw new InvalidImageException("No image provided");
        }
        final String lower = contentType != null ? contentType.toLowerCase() : "";
        if (!lower.contains("jpeg") && !lower.contains("jpg") && !lower.contains("png") && !lower.contains("webp") && !lower.contains("gif")) {
            throw new InvalidImageException("Unsupported image type. Use JPEG, PNG, WebP or GIF.");
        }

        byte[] inputBytes = inputStream.readAllBytes();
        if (inputBytes.length == 0) {
            throw new InvalidImageException("Empty file.");
        }
        if (inputBytes.length > MAX_INPUT_BYTES) {
            throw new ImageTooLargeException("Image is too large. Max 15 MB.");
        }

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(inputBytes));
        if (img == null) {
            throw new InvalidImageException("Could not read image. Please use a valid image file (JPEG, PNG, WebP or GIF).");
        }

        // Appliquer l'orientation EXIF pour ne pas inverser le sens (sans modifier le ratio)
        int orientation = getExifOrientation(inputBytes, lower);
        img = applyExifOrientation(img, orientation);

        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            throw new InvalidImageException("Invalid image dimensions.");
        }
        if (w > MAX_DIMENSION_PX || h > MAX_DIMENSION_PX) {
            throw new InvalidImageException("Image dimensions too large. Max 4000 px per side.");
        }

        // Toujours convertir en RGB (évite les erreurs avec PNG/WebP à transparence ou formats exotiques)
        img = toRgb(img, w, h);

        // Redimensionner uniquement en gardant le ratio (côté le plus long = MAX_LONGEST_SIDE_PX), pas d'inversion
        int longest = Math.max(w, h);
        if (longest > MAX_LONGEST_SIDE_PX) {
            double scale = (double) MAX_LONGEST_SIDE_PX / longest;
            int newW = Math.max(1, (int) Math.round(w * scale));
            int newH = Math.max(1, (int) Math.round(h * scale));
            img = scaleImage(img, newW, newH);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "jpg", baos)) {
            throw new InvalidImageException("Could not compress image to JPEG.");
        }
        byte[] bytes = baos.toByteArray();
        if (bytes.length > MAX_OUTPUT_BYTES) {
            throw new ImageTooLargeException("Image is too large after compression. Please use a smaller image.");
        }
        return bytes;
    }

    /** Convertit n'importe quel BufferedImage en TYPE_INT_RGB (fond blanc si transparence). */
    private BufferedImage toRgb(BufferedImage src, int w, int h) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Redimensionne en gardant le ratio, sans aucune inversion. */
    private BufferedImage scaleImage(BufferedImage src, int newW, int newH) {
        Image scaled = src.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(scaled, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Lit l'orientation EXIF (tag 274) dans les octets JPEG pour respecter le sens de la photo.
     * Retourne 1 si inconnu ou non présent.
     */
    private int getExifOrientation(byte[] data, String contentTypeLower) {
        if (data == null || data.length < 20) return 1;
        if (!contentTypeLower.contains("jpeg") && !contentTypeLower.contains("jpg")) return 1;
        try {
            int i = 0;
            while (i < data.length - 1) {
                if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xE1) {
                    int segLen = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
                    if (i + 4 + segLen > data.length) break;
                    if (i + 10 <= data.length
                            && data[i + 4] == 'E' && data[i + 5] == 'x' && data[i + 6] == 'i' && data[i + 7] == 'f') {
                        int tiffStart = i + 10;
                        if (tiffStart + 14 > data.length) break;
                        boolean bigEndian = data[tiffStart] == 0x4D && data[tiffStart + 1] == 0x4D;
                        int ifd0Offset = read32(data, tiffStart + 4, bigEndian);
                        int ifd0 = tiffStart + ifd0Offset;
                        if (ifd0 < 0 || ifd0 + 2 > data.length) break;
                        int numEntries = read16(data, ifd0, bigEndian);
                        for (int e = 0; e < numEntries && ifd0 + 2 + (e + 1) * 12 <= data.length; e++) {
                            int entry = ifd0 + 2 + e * 12;
                            int tag = read16(data, entry, bigEndian);
                            if (tag == 274) {
                                int val = read16(data, entry + 8, bigEndian);
                                if (val >= 1 && val <= 8) return val;
                                return 1;
                            }
                        }
                    }
                    i += 2 + segLen;
                } else {
                    i++;
                }
            }
        } catch (Exception ignored) {
            // garder orientation 1
        }
        return 1;
    }

    private static int read16(byte[] b, int off, boolean bigEndian) {
        if (bigEndian) return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int read32(byte[] b, int off, boolean bigEndian) {
        if (bigEndian) return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    /**
     * Applique l'orientation EXIF (1-8) : rotation/miroir pour afficher le bon sens, sans inverser arbitrairement.
     */
    private BufferedImage applyExifOrientation(BufferedImage src, int orientation) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (orientation == 1) return src;
        int outW = w, outH = h;
        double rad = 0;
        boolean flipH = false, flipV = false;
        switch (orientation) {
            case 2: flipH = true; break;
            case 3: rad = Math.PI; break;
            case 4: flipV = true; break;
            case 5: rad = -Math.PI / 2; outW = h; outH = w; flipH = true; break;  // 90 CW + flip H
            case 6: rad = -Math.PI / 2; outW = h; outH = w; break;                 // 90 CW
            case 7: rad = Math.PI / 2; outW = h; outH = w; flipH = true; break;    // 90 CCW + flip H
            case 8: rad = Math.PI / 2; outW = h; outH = w; break;                   // 90 CCW
            default: return src;
        }
        BufferedImage out = new BufferedImage(outW, outH, src.getType() != BufferedImage.TYPE_INT_RGB ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.translate(outW / 2.0, outH / 2.0);
            if (rad != 0) g.rotate(rad);
            if (flipH) g.scale(-1, 1);
            if (flipV) g.scale(1, -1);
            g.translate(-w / 2.0, -h / 2.0);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    public static int getMaxInputBytes() {
        return MAX_INPUT_BYTES;
    }

    public String toBase64(byte[] logo) {
        if (logo == null || logo.length == 0) return null;
        return Base64.getEncoder().encodeToString(logo);
    }
}
