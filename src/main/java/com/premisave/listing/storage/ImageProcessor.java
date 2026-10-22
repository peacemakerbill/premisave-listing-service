package com.premisave.listing.storage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded file into clean JPEG bytes: checks that it really is a
 * JPEG or PNG (by its first bytes, not the name or declared type), refuses
 * absurdly large pictures, applies the camera's rotation, and re-encodes it.
 * Re-encoding drops all metadata, including GPS position.
 */
public final class ImageProcessor {

    private ImageProcessor() {
    }

    public static boolean isJpeg(byte[] d) {
        return d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    public static boolean isPng(byte[] d) {
        return d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A;
    }

    /** Decodes, rotates upright and flattens onto white. Throws IllegalArgumentException for anything unusable. */
    public static BufferedImage decode(byte[] data, long maxPixels) {
        if (!isJpeg(data) && !isPng(data)) {
            throw new IllegalArgumentException("Only JPEG and PNG images are accepted.");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("The file is not a readable image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new IllegalArgumentException("The image is too large (more than " + maxPixels + " pixels).");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("The file is not a readable image.");
                }
                BufferedImage upright = applyOrientation(image, isJpeg(data) ? exifOrientation(data) : 1);
                return flatten(upright);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new IllegalArgumentException("The file is not a readable image.");
        }
    }

    /** Scales down to the given width if wider; never scales up. */
    public static BufferedImage limitWidth(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int targetHeight = Math.max(1, (int) Math.round((double) source.getHeight() * maxWidth / source.getWidth()));
        BufferedImage current = source;
        // Halve repeatedly while far larger than the target; one big jump looks jagged.
        while (current.getWidth() / 2 >= maxWidth) {
            current = resize(current, current.getWidth() / 2, Math.max(1, current.getHeight() / 2));
        }
        return resize(current, maxWidth, targetHeight);
    }

    public static byte[] toJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** JPEG has no transparency: draw onto white as plain RGB. */
    private static BufferedImage flatten(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target.getWidth(), target.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /**
     * Rotates/flips the picture the way the EXIF orientation tag says (1 to
     * 8), so photos taken in portrait stay upright once the metadata is gone.
     */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation < 2 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            default -> new AffineTransform(0, -1, 1, 0, 0, w);
        };
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.drawImage(src, t, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Reads the orientation tag from a JPEG's EXIF block. Returns 1 (upright) if there is none. */
    static int exifOrientation(byte[] d) {
        try {
            int pos = 2;
            while (pos + 4 < d.length) {
                if ((d[pos] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = d[pos + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) {
                    return 1;
                }
                int length = ((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF);
                if (marker == 0xE1 && length >= 16 && d[pos + 4] == 'E' && d[pos + 5] == 'x'
                        && d[pos + 6] == 'i' && d[pos + 7] == 'f') {
                    return orientationFromTiff(d, pos + 10, pos + 2 + length);
                }
                pos += 2 + length;
            }
        } catch (RuntimeException e) {
            return 1;
        }
        return 1;
    }

    private static int orientationFromTiff(byte[] d, int tiff, int end) {
        boolean little = d[tiff] == 'I';
        int ifd = tiff + (int) readInt(d, tiff + 4, 4, little);
        if (ifd + 2 > end) {
            return 1;
        }
        int entries = (int) readInt(d, ifd, 2, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if (readInt(d, entry, 2, little) == 0x0112) {
                int value = (int) readInt(d, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static long readInt(byte[] d, int offset, int bytes, boolean little) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            int b = d[offset + (little ? bytes - 1 - i : i)] & 0xFF;
            value = (value << 8) | b;
        }
        return value;
    }
}
