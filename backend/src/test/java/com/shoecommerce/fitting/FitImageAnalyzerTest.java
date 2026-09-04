package com.shoecommerce.fitting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class FitImageAnalyzerTest {
    private static final int IMAGE_WIDTH = 1200;
    private static final int IMAGE_HEIGHT = 1500;
    private static final double A4_WIDTH_MM = 210;
    private static final double A4_HEIGHT_MM = 297;
    private final FitImageAnalyzer analyzer = new FitImageAnalyzer();

    @Test
    void measuresIndependentControlledStressFixturesInMillimetres() {
        assertMeasurement("clean-overhead", overhead(251, 98, 270, 220, 660, 930, .5, .5, false), 251, 98, 4);
        assertMeasurement("off-center-shorter-foot", overhead(235, 90, 420, 160, 660, 930, .64, .43, false), 235, 90, 4);
        assertMeasurement("near-edge-longer-foot", overhead(270, 105, 16, 100, 660, 930, .5, .5, false), 270, 105, 5);
        assertMeasurement("moderate-rotation", rotated(251, 98), 251, 98, 8);
        assertMeasurement("moderate-projective", projective(270, 105), 270, 105, 10);
        assertMeasurement("shadow-and-noise", noisy(235, 90), 235, 90, 8);
    }

    @Test
    void rejectsFixturesThatCannotSupportARecommendation() {
        assertRetake("missing-reference", background(), FitImageAnalyzer.RetakeReason.REFERENCE_NOT_FOUND);
        assertRetake("clipped-reference", overhead(251, 98, 2, 220, 660, 930, .5, .5, false), FitImageAnalyzer.RetakeReason.REFERENCE_CLIPPED);
        assertRetake("partial-foot", overhead(251, 98, 270, 220, 660, 930, .5, .02, false), FitImageAnalyzer.RetakeReason.FOOT_PARTIAL);
        assertRetake("blur", blurred(overhead(251, 98, 270, 220, 660, 930, .5, .5, false)), FitImageAnalyzer.RetakeReason.IMAGE_TOO_BLURRY);
    }

    @Test
    void verifiesTheCommittedDemoFixtures() {
        assertMeasurement("committed-valid-a4-foot", committed("valid-a4-foot.png"), 251, 98, 4);
        assertRetake("committed-missing-reference", committed("invalid-no-reference.png"), FitImageAnalyzer.RetakeReason.REFERENCE_NOT_FOUND);
        assertRetake("committed-clipped-reference", committed("invalid-clipped-sheet.png"), FitImageAnalyzer.RetakeReason.REFERENCE_CLIPPED);
        assertRetake("committed-blur", committed("invalid-blurred.png"), FitImageAnalyzer.RetakeReason.IMAGE_TOO_BLURRY);
    }

    @Test
    void refusesInvalidFormatSizeAndDimensions() {
        assertThatThrownBy(() -> analyzer.analyze(new byte[] {1, 2, 3}))
                .hasMessage("Only real PNG and JPEG images are supported.");
        assertThatThrownBy(() -> analyzer.analyze(encoded(900, 1200, "GIF")))
                .hasMessage("Only real PNG and JPEG images are supported.");
        assertThatThrownBy(() -> analyzer.analyze(new byte[FitImageAnalyzer.MAX_BYTES + 1]))
                .hasMessage("The image must be 5 MB or smaller.");
        assertThatThrownBy(() -> analyzer.analyze(smallImage()))
                .hasMessage("Use an image at least 480 px per side and no more than 12 megapixels.");
    }

    private void assertMeasurement(String name, byte[] bytes, double length, double width, double tolerance) {
        FitImageAnalyzer.Analysis result = analyzer.analyze(bytes);
        System.out.printf(Locale.ROOT, "FIT_FIXTURE %s expected=%.1fx%.1f measured=%sx%s score=%s%n", name,
                length, width, result.footLengthMm(), result.footWidthMm(), result.analysisScore());
        assertThat(result.successful()).as(name).isTrue();
        assertThat(Math.abs(result.footLengthMm() - length)).as(name + " length error").isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(result.footWidthMm() - width)).as(name + " width error").isLessThanOrEqualTo(tolerance);
    }

    private void assertRetake(String name, byte[] bytes, FitImageAnalyzer.RetakeReason expected) {
        FitImageAnalyzer.Analysis result = analyzer.analyze(bytes);
        System.out.printf("FIT_FIXTURE %s result=%s reason=%s%n", name, result.status(), result.retakeReason());
        assertThat(result.status()).isEqualTo("RETAKE");
        assertThat(result.retakeReason()).isEqualTo(expected);
    }

    private static byte[] overhead(double length, double width, int x, int y, int pageWidth, int pageHeight,
            double footX, double footY, boolean noise) {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setColor(sheet());
        graphics.fillRect(x, y, pageWidth, pageHeight);
        if (noise) addShadowAndNoise(graphics, x, y, pageWidth, pageHeight);
        drawFoot(graphics, x + pageWidth * footX, y + pageHeight * footY,
                width / A4_WIDTH_MM * pageWidth, length / A4_HEIGHT_MM * pageHeight);
        graphics.dispose();
        return encoded(image, "PNG");
    }

    private static byte[] rotated(double length, double width) {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.translate(IMAGE_WIDTH / 2d, IMAGE_HEIGHT / 2d);
        graphics.rotate(Math.toRadians(14));
        graphics.translate(-IMAGE_WIDTH / 2d, -IMAGE_HEIGHT / 2d);
        graphics.setColor(sheet());
        graphics.fillRect(270, 285, 660, 930);
        drawFoot(graphics, 600, 750, width / A4_WIDTH_MM * 660, length / A4_HEIGHT_MM * 930);
        graphics.dispose();
        return encoded(image, "PNG");
    }

    private static byte[] projective(double length, double width) {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        Point[] page = {new Point(0, 0), new Point(A4_WIDTH_MM, 0), new Point(A4_WIDTH_MM, A4_HEIGHT_MM), new Point(0, A4_HEIGHT_MM)};
        Point[] imageCorners = {new Point(290, 135), new Point(920, 175), new Point(720, 1280), new Point(90, 1080)};
        Projection projection = Projection.from(page, imageCorners);
        Path2D sheet = new Path2D.Double();
        for (int index = 0; index < imageCorners.length; index++) {
            Point point = imageCorners[index];
            if (index == 0) sheet.moveTo(point.x, point.y); else sheet.lineTo(point.x, point.y);
        }
        sheet.closePath();
        graphics.setColor(sheet());
        graphics.fill(sheet);
        graphics.setColor(foot());
        Path2D silhouette = new Path2D.Double();
        for (int index = 0; index <= 160; index++) {
            double angle = Math.PI * 2 * index / 160d;
            Point point = projection.map(A4_WIDTH_MM / 2 + width / 2 * Math.cos(angle),
                    A4_HEIGHT_MM / 2 + length / 2 * Math.sin(angle));
            if (index == 0) silhouette.moveTo(point.x, point.y); else silhouette.lineTo(point.x, point.y);
        }
        silhouette.closePath();
        graphics.fill(silhouette);
        graphics.dispose();
        return encoded(image, "PNG");
    }

    private static byte[] noisy(double length, double width) {
        return overhead(length, width, 270, 220, 660, 930, .5, .5, true);
    }

    private static byte[] background() {
        return encoded(canvas(), "PNG");
    }

    private static byte[] blurred(byte[] source) {
        try {
            BufferedImage input = ImageIO.read(new ByteArrayInputStream(source));
            float[] values = new float[361];
            for (int index = 0; index < values.length; index++) values[index] = 1f / values.length;
            BufferedImage output = new ConvolveOp(new Kernel(19, 19, values), ConvolveOp.EDGE_ZERO_FILL, null).filter(input, null);
            return encoded(output, "PNG");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static BufferedImage canvas() {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(35, 35, 35));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return graphics;
    }

    private static void drawFoot(Graphics2D graphics, double centerX, double centerY, double width, double length) {
        graphics.setColor(foot());
        graphics.fill(new RoundRectangle2D.Double(centerX - width / 2, centerY - length / 2, width, length, width, width));
    }

    private static void addShadowAndNoise(Graphics2D graphics, int x, int y, int width, int height) {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .16f));
        graphics.setColor(new Color(90, 90, 90));
        graphics.fillOval(x + width / 8, y + height / 4, width * 3 / 4, height / 3);
        Random random = new Random(17);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .20f));
        for (int index = 0; index < 500; index++) {
            graphics.setColor(random.nextBoolean() ? Color.WHITE : new Color(150, 145, 138));
            graphics.fillRect(x + random.nextInt(width), y + random.nextInt(height), 1, 1);
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private static Color sheet() { return new Color(247, 247, 244); }
    private static Color foot() { return new Color(38, 42, 48); }

    private static byte[] smallImage() {
        return encoded(new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB), "PNG");
    }

    private static byte[] committed(String filename) {
        try {
            return Files.readAllBytes(Path.of("..", "docs", "demo-assets", "fit", filename));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] encoded(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, format, output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] encoded(int width, int height, String format) {
        return encoded(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format);
    }

    // Independent test renderer: it maps known A4 coordinates into image pixels,
    // while production performs the inverse map from detected pixels to A4 millimetres.
    private record Projection(double a, double b, double c, double d, double e, double f, double g, double h) {
        static Projection from(Point[] source, Point[] target) {
            double[][] matrix = new double[8][9];
            for (int index = 0; index < 4; index++) {
                double x = source[index].x, y = source[index].y, u = target[index].x, v = target[index].y;
                matrix[index * 2] = new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
                matrix[index * 2 + 1] = new double[] {0, 0, 0, x, y, 1, -v * x, -v * y, v};
            }
            for (int column = 0; column < 8; column++) {
                int pivot = column;
                for (int row = column + 1; row < 8; row++) if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
                double[] swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap;
                double divisor = matrix[column][column];
                for (int item = column; item < 9; item++) matrix[column][item] /= divisor;
                for (int row = 0; row < 8; row++) if (row != column) {
                    double factor = matrix[row][column];
                    for (int item = column; item < 9; item++) matrix[row][item] -= factor * matrix[column][item];
                }
            }
            double[] value = new double[8];
            for (int index = 0; index < 8; index++) value[index] = matrix[index][8];
            return new Projection(value[0], value[1], value[2], value[3], value[4], value[5], value[6], value[7]);
        }

        Point map(double x, double y) {
            double denominator = g * x + h * y + 1;
            return new Point((a * x + b * y + c) / denominator, (d * x + e * y + f) / denominator);
        }
    }

    private record Point(double x, double y) { }
}
