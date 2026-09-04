package com.shoecommerce.fitting;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import com.shoecommerce.platform.api.InvalidRequestException;

@Component
public final class FitImageAnalyzer {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000;
    private static final int MIN_SIDE = 480;

    public Analysis analyze(byte[] bytes) {
        BufferedImage image = decode(bytes);
        int stride = Math.max(1, (int) Math.ceil(Math.max(image.getWidth(), image.getHeight()) / 1400d));
        int width = (image.getWidth() + stride - 1) / stride;
        int height = (image.getHeight() + stride - 1) / stride;
        int[] rgb = new int[width * height];
        int[] gray = new int[rgb.length];
        boolean[] reference = new boolean[rgb.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getRGB(Math.min(x * stride, image.getWidth() - 1),
                        Math.min(y * stride, image.getHeight() - 1));
                int index = y * width + x;
                rgb[index] = color;
                int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                gray[index] = (red * 299 + green * 587 + blue * 114) / 1000;
                reference[index] = red >= 180 && green >= 180 && blue >= 180
                        && Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 45;
            }
        }

        Corners corners = largestReference(reference, width, height, stride);
        if (corners == null || corners.count < rgb.length * 0.05) return Analysis.retake(RetakeReason.REFERENCE_NOT_FOUND);
        if (corners.nearEdge(image.getWidth(), image.getHeight())) return Analysis.retake(RetakeReason.REFERENCE_CLIPPED);

        double top = distance(corners.topLeft, corners.topRight);
        double right = distance(corners.topRight, corners.bottomRight);
        double bottom = distance(corners.bottomRight, corners.bottomLeft);
        double left = distance(corners.bottomLeft, corners.topLeft);
        double longSide = Math.max((top + bottom) / 2, (left + right) / 2);
        double shortSide = Math.min((top + bottom) / 2, (left + right) / 2);
        double ratio = longSide / shortSide;
        double area = polygonArea(corners);
        if (area < image.getWidth() * image.getHeight() * 0.12 || ratio < 1.18 || ratio > 1.72) {
            return Analysis.retake(RetakeReason.REFERENCE_NOT_FOUND);
        }
        double perspective = Math.max(Math.max(top, bottom) / Math.min(top, bottom),
                Math.max(left, right) / Math.min(left, right));
        if (perspective > 1.72) return Analysis.retake(RetakeReason.EXCESSIVE_PERSPECTIVE);

        double pageWidth = (top + bottom) <= (left + right) ? 210 : 297;
        double pageHeight = pageWidth == 210 ? 297 : 210;
        Transform transform;
        try { transform = Transform.from(corners, pageWidth, pageHeight); }
        catch (IllegalArgumentException exception) { return Analysis.retake(RetakeReason.EXCESSIVE_PERSPECTIVE); }
        double sharpness = sharpness(gray, width, height);
        if (sharpness < 5.0) return Analysis.retake(RetakeReason.IMAGE_TOO_BLURRY);

        Component foot = largestFoot(rgb, width, height, stride, transform, pageWidth, pageHeight);
        if (foot == null || foot.count < rgb.length * 0.006) return Analysis.retake(RetakeReason.FOOT_NOT_FOUND);
        Measurement measurement = measure(foot, width, stride, transform, pageWidth, pageHeight);
        if (measurement.partial) return Analysis.retake(RetakeReason.FOOT_PARTIAL);
        if (measurement.lengthMm < 180 || measurement.lengthMm > 330
                || measurement.widthMm < 65 || measurement.widthMm > 130) {
            return Analysis.retake(RetakeReason.IMPLAUSIBLE_MEASUREMENT);
        }

        double referenceScore = clamp(100 - Math.abs(ratio - 297d / 210) * 140);
        double perspectiveScore = clamp(115 - (perspective - 1) * 115);
        double sharpnessScore = clamp((sharpness - 4) * 8.5);
        double componentRatio = foot.count / (double) corners.count;
        double segmentationScore = clamp(100 - Math.abs(componentRatio - 0.22) * 350);
        int score = (int) Math.round(referenceScore * .30 + perspectiveScore * .20
                + sharpnessScore * .25 + segmentationScore * .25);
        if (score < 58) return Analysis.retake(RetakeReason.ANALYSIS_INSUFFICIENT);
        return Analysis.success(round(measurement.lengthMm), round(measurement.widthMm), score,
                (int) Math.round(referenceScore), (int) Math.round(perspectiveScore),
                (int) Math.round(sharpnessScore), (int) Math.round(segmentationScore));
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new InvalidRequestException("FIT_IMAGE_EMPTY", "Choose a PNG or JPEG image.");
        if (bytes.length > MAX_BYTES) throw new InvalidRequestException("FIT_IMAGE_TOO_LARGE", "The image must be 5 MB or smaller.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw invalidFormat();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidFormat();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!("PNG".equalsIgnoreCase(format) || "JPEG".equalsIgnoreCase(format))) throw invalidFormat();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < MIN_SIDE || height < MIN_SIDE || (long) width * height > MAX_PIXELS) {
                    throw new InvalidRequestException("FIT_IMAGE_DIMENSIONS_INVALID",
                            "Use an image at least 480 px per side and no more than 12 megapixels.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw invalidFormat();
                return image;
            } finally { reader.dispose(); }
        } catch (IOException exception) {
            throw new InvalidRequestException("FIT_IMAGE_INVALID", "The image could not be decoded.");
        }
    }

    private static InvalidRequestException invalidFormat() {
        return new InvalidRequestException("FIT_IMAGE_FORMAT_UNSUPPORTED", "Only real PNG and JPEG images are supported.");
    }

    private static Corners largestReference(boolean[] mask, int width, int height, int stride) {
        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[mask.length];
        Corners best = null;
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            Corners current = new Corners();
            while (head < tail) {
                int index = queue[head++], x = index % width, y = index / width;
                current.accept(x * stride, y * stride);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = ny * width + nx;
                    if (mask[next] && !seen[next]) { seen[next] = true; queue[tail++] = next; }
                }
            }
            if (best == null || current.count > best.count) best = current;
        }
        return best;
    }

    private static Component largestFoot(int[] rgb, int width, int height, int stride, Transform transform,
            double pageWidth, double pageHeight) {
        boolean[] mask = new boolean[rgb.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Point point = transform.map(x * stride, y * stride);
            if (point.x < 3 || point.y < 3 || point.x > pageWidth - 3 || point.y > pageHeight - 3) continue;
            int color = rgb[y * width + x];
            int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
            int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
            int chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
            mask[y * width + x] = luminance < 188 || (luminance < 210 && chroma > 50);
        }
        int[] labels = new int[mask.length], queue = new int[mask.length];
        int label = 0, bestLabel = 0, bestCount = 0;
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || labels[start] != 0) continue;
            label++;
            int head = 0, tail = 0;
            queue[tail++] = start; labels[start] = label;
            while (head < tail) {
                int index = queue[head++], x = index % width, y = index / width;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int next = ny * width + nx;
                    if (mask[next] && labels[next] == 0) { labels[next] = label; queue[tail++] = next; }
                }
            }
            if (tail > bestCount) { bestCount = tail; bestLabel = label; }
        }
        return bestLabel == 0 ? null : new Component(labels, bestLabel, bestCount);
    }

    private static Measurement measure(Component component, int width, int stride, Transform transform,
            double pageWidth, double pageHeight) {
        double meanX = 0, meanY = 0;
        for (int index = 0; index < component.labels.length; index++) if (component.labels[index] == component.label) {
            Point p = transform.map(index % width * stride, index / width * stride);
            meanX += p.x; meanY += p.y;
        }
        meanX /= component.count; meanY /= component.count;
        double xx = 0, yy = 0, xy = 0;
        for (int index = 0; index < component.labels.length; index++) if (component.labels[index] == component.label) {
            Point p = transform.map(index % width * stride, index / width * stride);
            double dx = p.x - meanX, dy = p.y - meanY;
            xx += dx * dx; yy += dy * dy; xy += dx * dy;
        }
        double angle = .5 * Math.atan2(2 * xy, xx - yy);
        double ux = Math.cos(angle), uy = Math.sin(angle), vx = -uy, vy = ux;
        double minMajor = Double.MAX_VALUE, maxMajor = -Double.MAX_VALUE;
        double minMinor = Double.MAX_VALUE, maxMinor = -Double.MAX_VALUE;
        double minPageX = Double.MAX_VALUE, maxPageX = -Double.MAX_VALUE;
        double minPageY = Double.MAX_VALUE, maxPageY = -Double.MAX_VALUE;
        for (int index = 0; index < component.labels.length; index++) if (component.labels[index] == component.label) {
            Point p = transform.map(index % width * stride, index / width * stride);
            double major = p.x * ux + p.y * uy, minor = p.x * vx + p.y * vy;
            minMajor = Math.min(minMajor, major); maxMajor = Math.max(maxMajor, major);
            minMinor = Math.min(minMinor, minor); maxMinor = Math.max(maxMinor, minor);
            minPageX = Math.min(minPageX, p.x); maxPageX = Math.max(maxPageX, p.x);
            minPageY = Math.min(minPageY, p.y); maxPageY = Math.max(maxPageY, p.y);
        }
        double first = maxMajor - minMajor, second = maxMinor - minMinor;
        boolean partial = minPageX < 5 || minPageY < 5 || maxPageX > pageWidth - 5 || maxPageY > pageHeight - 5;
        return new Measurement(Math.max(first, second), Math.min(first, second), partial);
    }

    private static double sharpness(int[] gray, int width, int height) {
        double total = 0; int count = 0;
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            int index = y * width + x;
            int laplacian = gray[index] * 4 - gray[index - 1] - gray[index + 1]
                    - gray[index - width] - gray[index + width];
            total += (double) laplacian * laplacian; count++;
        }
        return Math.sqrt(total / Math.max(1, count));
    }

    private static double polygonArea(Corners c) {
        Point[] points = {c.topLeft, c.topRight, c.bottomRight, c.bottomLeft};
        double sum = 0;
        for (int i = 0; i < points.length; i++) {
            Point a = points[i], b = points[(i + 1) % points.length];
            sum += a.x * b.y - b.x * a.y;
        }
        return Math.abs(sum) / 2;
    }

    private static double distance(Point a, Point b) { return Math.hypot(a.x - b.x, a.y - b.y); }
    private static double clamp(double value) { return Math.max(0, Math.min(100, value)); }
    private static double round(double value) { return Math.round(value * 10) / 10d; }

    public enum RetakeReason {
        REFERENCE_NOT_FOUND, REFERENCE_CLIPPED, EXCESSIVE_PERSPECTIVE, IMAGE_TOO_BLURRY,
        FOOT_NOT_FOUND, FOOT_PARTIAL, IMPLAUSIBLE_MEASUREMENT, ANALYSIS_INSUFFICIENT
    }

    public record Analysis(String status, RetakeReason retakeReason, Double footLengthMm, Double footWidthMm,
            Integer analysisScore, Integer referenceScore, Integer perspectiveScore, Integer sharpnessScore,
            Integer segmentationScore) {
        static Analysis retake(RetakeReason reason) { return new Analysis("RETAKE", reason, null, null, null, null, null, null, null); }
        static Analysis success(double length, double width, int score, int reference, int perspective,
                int sharpness, int segmentation) {
            return new Analysis("SUCCESS", null, length, width, score, reference, perspective, sharpness, segmentation);
        }
        public boolean successful() { return "SUCCESS".equals(status); }
    }

    private static final class Corners {
        private Point topLeft, topRight, bottomRight, bottomLeft;
        private double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        private double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;
        private int count;
        void accept(double x, double y) {
            count++;
            double sum = x + y, diff = x - y;
            if (sum < minSum) { minSum = sum; topLeft = new Point(x, y); }
            if (sum > maxSum) { maxSum = sum; bottomRight = new Point(x, y); }
            if (diff > maxDiff) { maxDiff = diff; topRight = new Point(x, y); }
            if (diff < minDiff) { minDiff = diff; bottomLeft = new Point(x, y); }
        }
        boolean nearEdge(int width, int height) {
            Point[] points = {topLeft, topRight, bottomRight, bottomLeft};
            double margin = Math.max(3, Math.min(width, height) * .005);
            for (Point p : points) if (p.x < margin || p.y < margin || p.x > width - 1 - margin || p.y > height - 1 - margin) return true;
            return false;
        }
    }

    private record Point(double x, double y) { }
    private record Component(int[] labels, int label, int count) { }
    private record Measurement(double lengthMm, double widthMm, boolean partial) { }

    private record Transform(double a, double b, double c, double d, double e, double f, double g, double h) {
        static Transform from(Corners corners, double width, double height) {
            Point[] source = {corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft};
            Point[] target = {new Point(0, 0), new Point(width, 0), new Point(width, height), new Point(0, height)};
            double[][] matrix = new double[8][9];
            for (int i = 0; i < 4; i++) {
                double x = source[i].x, y = source[i].y, u = target[i].x, v = target[i].y;
                matrix[i * 2] = new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
                matrix[i * 2 + 1] = new double[] {0, 0, 0, x, y, 1, -v * x, -v * y, v};
            }
            for (int column = 0; column < 8; column++) {
                int pivot = column;
                for (int row = column + 1; row < 8; row++) if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
                double[] swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap;
                if (Math.abs(matrix[column][column]) < 1e-9) throw new IllegalArgumentException("Invalid reference geometry");
                double divisor = matrix[column][column];
                for (int item = column; item < 9; item++) matrix[column][item] /= divisor;
                for (int row = 0; row < 8; row++) if (row != column) {
                    double factor = matrix[row][column];
                    for (int item = column; item < 9; item++) matrix[row][item] -= factor * matrix[column][item];
                }
            }
            double[] value = new double[8];
            for (int i = 0; i < 8; i++) value[i] = matrix[i][8];
            return new Transform(value[0], value[1], value[2], value[3], value[4], value[5], value[6], value[7]);
        }
        Point map(double x, double y) {
            double denominator = g * x + h * y + 1;
            return new Point((a * x + b * y + c) / denominator, (d * x + e * y + f) / denominator);
        }
    }
}
