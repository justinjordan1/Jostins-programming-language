package src.Evaluation;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

public class PixelCanvas {
    private BufferedImage image;
    private JFrame frame;
    private CanvasPanel panel;
    private int windowWidth;
    private int windowHeight;

    private List<BufferedImage> frames = new ArrayList<>();

    public PixelCanvas(int width, int height) {
        this(width, height, width, height);
    }

    public PixelCanvas(int width, int height, int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        setGridSize(width, height);
    }

    public void setGridSize(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        refreshWindow();
    }

    public void setWindowSize(int width, int height) {
        windowWidth = width;
        windowHeight = height;
        refreshWindow();
    }

    private void refreshWindow() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        if (frame == null) {
            frame = new JFrame("Jostin Canvas");
            panel = new CanvasPanel();
            frame.add(panel);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        }

        panel.setPreferredSize(new Dimension(windowWidth, windowHeight));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.repaint();
    }

    public void setPixel(int x, int y, int red, int green, int blue) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }

        int rgb = (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
        image.setRGB(x, y, rgb);

        if (panel != null) {
            panel.repaint();
        }
    }

    public void exportPPM(String filename) {
        if (image == null) return;
        try {
            FileWriter writer = new FileWriter(filename);
            int width = image.getWidth();
            int height = image.getHeight();
            writer.write("P3\n" + width + " " + height + "\n255\n");

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    writer.write(r + " " + g + " " + b + "\n");
                }
            }
            writer.close();
        } catch (IOException e) {
            System.out.println("Failed to export PPM: " + e.getMessage());
        }
    }

    public void captureFrame() {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = copy.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        frames.add(copy);
    }

    public void collapse(String filename, int scaleFactor) {
        if (frames.isEmpty()) {
            System.out.println("No frames captured! Call captureFrame() in your loop.");
            return;
        }

        try {
            ImageOutputStream output = new FileImageOutputStream(new File(filename));
            ImageWriter gifWriter = ImageIO.getImageWritersByFormatName("gif").next();
            gifWriter.setOutput(output);
            gifWriter.prepareWriteSequence(null);

            for (BufferedImage rawFrame : frames) {
                int newWidth = rawFrame.getWidth() * scaleFactor;
                int newHeight = rawFrame.getHeight() * scaleFactor;
                BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = scaled.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2d.drawImage(rawFrame, 0, 0, newWidth, newHeight, null);
                g2d.dispose();

                BufferedImage indexed = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_INDEXED);
                indexed.getGraphics().drawImage(scaled, 0, 0, null);

                ImageWriteParam param = gifWriter.getDefaultWriteParam();
                IIOMetadata meta = gifWriter.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), param);
                configureGIFMeta(meta, 100);

                gifWriter.writeToSequence(new IIOImage(indexed, null, meta), param);
            }

            gifWriter.endWriteSequence();
            output.close();
            System.out.println("Successfully compiled GIF: " + filename);
            frames.clear();

        } catch (Exception e) {
            System.out.println("Failed to export GIF: " + e.getMessage());
        }
    }

    private void configureGIFMeta(IIOMetadata meta, int delayMs) throws Exception {
        String format = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);

        IIOMetadataNode graphicsControl = new IIOMetadataNode("GraphicControlExtension");
        graphicsControl.setAttribute("disposalMethod", "restoreToBackgroundColor");
        graphicsControl.setAttribute("userInputFlag", "FALSE");
        graphicsControl.setAttribute("transparentColorFlag", "FALSE");
        graphicsControl.setAttribute("delayTime", Integer.toString(delayMs / 10));
        graphicsControl.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode appExtensions = new IIOMetadataNode("ApplicationExtensions");
        IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
        appExtension.setAttribute("applicationID", "NETSCAPE");
        appExtension.setAttribute("authenticationCode", "2.0");
        appExtension.setUserObject(new byte[]{1, 0, 0});
        appExtensions.appendChild(appExtension);

        root.appendChild(graphicsControl);
        root.appendChild(appExtensions);
        meta.setFromTree(format, root);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private class CanvasPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics;
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics2D.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
