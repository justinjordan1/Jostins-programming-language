package src.Evaluation;

public class PixelCanvas {
    public PixelCanvas(int width, int height) {
        this(width, height, width, height);
    }

    public PixelCanvas(int width, int height, int windowWidth, int windowHeight) {
        throw new IllegalStateException("Canvas isn't available in the browser; use image() and pixel()");
    }

    public void setGridSize(int width, int height) {
    }

    public void setWindowSize(int width, int height) {
    }

    public void setPixel(int x, int y, int red, int green, int blue) {
    }

    public void exportPPM(String filename) {
    }

    public void captureFrame() {
    }

    public void collapse(String filename, int scaleFactor) {
    }
}
