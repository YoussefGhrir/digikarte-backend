package ghrir.digikarte.service;

import ghrir.digikarte.exception.ImageTooLargeException;
import ghrir.digikarte.exception.InvalidImageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@Service
@Slf4j
public class ProfilePhotoService {

    private static final int MAX_INPUT_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final int MAX_SIDE_PX = 400;
    private static final int MAX_OUTPUT_BYTES = 300 * 1024;   // 300 KB after compression
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Validates, optionally crops/resizes and compresses image to JPEG.
     * @return JPEG bytes (base64 not applied here)
     */
    public byte[] processProfilePhoto(InputStream inputStream, String contentType) {
        if (inputStream == null) {
            throw new InvalidImageException("No image provided");
        }
        final String lower = contentType != null ? contentType.toLowerCase() : "";
        if (!lower.contains("jpeg") && !lower.contains("jpg") && !lower.contains("png") && !lower.contains("webp") && !lower.contains("gif")) {
            throw new InvalidImageException("Unsupported image type. Use JPEG, PNG, WebP or GIF.");
        }

        BufferedImage img;
        try {
            img = ImageIO.read(inputStream);
        } catch (IOException e) {
            log.warn("Failed to read image", e);
            throw new InvalidImageException("Could not read image. Please use a valid image file.");
        }
        if (img == null) {
            throw new InvalidImageException("Could not read image. Please use a valid image file.");
        }

        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            throw new InvalidImageException("Invalid image dimensions.");
        }

        // Scale down if too large (crop to square then resize to fit MAX_SIDE_PX)
        int side = Math.min(w, h);
        int x0 = (w - side) / 2;
        int y0 = (h - side) / 2;
        if (side > MAX_SIDE_PX) {
            double scale = (double) MAX_SIDE_PX / side;
            int newW = (int) Math.round(side * scale);
            int newH = newW;
            BufferedImage cropped = img.getSubimage(x0, y0, side, side);
            Image scaled = cropped.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
            BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
            img = out;
        } else if (x0 != 0 || y0 != 0) {
            img = img.getSubimage(x0, y0, side, side);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(img, "jpg", baos)) {
                throw new InvalidImageException("Could not compress image.");
            }
        } catch (IOException e) {
            throw new InvalidImageException("Could not process image.");
        }
        byte[] bytes = baos.toByteArray();
        if (bytes.length > MAX_OUTPUT_BYTES) {
            throw new ImageTooLargeException("Image is too large after compression. Please use a smaller image.");
        }
        return bytes;
    }

    public static int getMaxInputBytes() {
        return MAX_INPUT_BYTES;
    }

    public String toBase64(byte[] photo) {
        if (photo == null || photo.length == 0) return null;
        return Base64.getEncoder().encodeToString(photo);
    }
}
