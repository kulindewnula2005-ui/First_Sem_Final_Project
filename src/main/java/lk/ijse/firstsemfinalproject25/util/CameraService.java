package lk.ijse.firstsemfinalproject25.util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens a webcam, keeps the newest picture, and draws a phone-scanner style overlay on the preview:
 * a dimmed picture, a rounded frame with thick corner brackets and a moving scan line.
 */
public class CameraService {

    /** Try 1280x720 first (better for reading plates); falls back to 640x480. */
    private static final boolean PREFER_HD = true;

    /** The preview is drawn at most this wide (keeps the live picture smooth). */
    private static final int PREVIEW_WIDTH = 960;

    // the frame in the middle of the picture, as a share of the picture size
    private static final double GX = 0.20;
    private static final double GY = 0.28;
    private static final double GW = 0.60;
    private static final double GH = 0.44;

    private List<Webcam> cameras;
    private Webcam webcam;
    private Thread loop;

    private volatile boolean running;
    private volatile int guideState;        // 0 = scanning (blue), 1 = reading (amber), 2 = found (green), 3 = check (amber)
    private volatile boolean scanLine = true;
    private volatile String error;

    private final AtomicReference<BufferedImage> rawFrame = new AtomicReference<>();
    private final AtomicReference<BufferedImage> shownFrame = new AtomicReference<>();

    // the dimmed area around the frame is drawn once per picture size
    private BufferedImage maskImage;
    private int maskW;
    private int maskH;

    /* ======================= cameras ======================= */
    public synchronized List<String> findCameras() throws Exception {
        try {
            cameras = Webcam.getWebcams();
            List<String> names = new ArrayList<>();
            for (Webcam cam : cameras) {
                names.add(cam.getName());
            }
            if (names.isEmpty()) {
                throw new Exception("No camera was found. Plug in a webcam, and in the Windows privacy "
                        + "settings allow desktop apps to use the camera.");
            }
            return names;
        } catch (Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw new Exception("The camera library could not start (" + t.getClass().getSimpleName()
                    + ": " + t.getMessage() + ").");
        }
    }

    public synchronized void start(int index) throws Exception {
        stop();
        error = null;
        try {
            if (cameras == null || cameras.isEmpty()) {
                cameras = Webcam.getWebcams();
            }
            if (cameras.isEmpty()) {
                throw new Exception("No camera was found.");
            }
            Webcam cam = cameras.get(Math.max(0, Math.min(index, cameras.size() - 1)));
            open(cam);
            webcam = cam;
        } catch (Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw new Exception("The camera library could not start (" + t.getClass().getSimpleName()
                    + ": " + t.getMessage() + ").");
        }

        running = true;
        loop = new Thread(this::run, "camera-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void open(Webcam cam) throws Exception {
        Dimension[] sizes = PREFER_HD
                ? new Dimension[]{WebcamResolution.HD.getSize(), WebcamResolution.VGA.getSize()}
                : new Dimension[]{WebcamResolution.VGA.getSize()};
        Exception last = null;
        for (Dimension size : sizes) {
            try {
                cam.setViewSize(size);
                cam.open();
                return;
            } catch (Exception ex) {
                last = ex;
                try {
                    cam.close();
                } catch (Exception ignore) {
                    // already closed
                }
            }
        }
        throw last;
    }

    public synchronized void stop() {
        running = false;
        Thread t = loop;
        loop = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
        }
        Webcam cam = webcam;
        webcam = null;
        if (cam != null) {
            try {
                cam.close();
            } catch (Throwable ignore) {
                // nothing more to do
            }
        }
        rawFrame.set(null);
        shownFrame.set(null);
    }

    private void run() {
        Webcam cam = webcam;
        try {
            while (running && cam != null) {
                BufferedImage img = cam.getImage();
                if (img == null) {
                    Thread.sleep(30);
                    continue;
                }
                rawFrame.set(img);
                shownFrame.set(render(img, guideState, scanLine));
                Thread.sleep(25);
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            if (running) {
                error = "The camera stopped (" + t.getMessage() + ").";
            }
        } finally {
            running = false;
        }
    }

    /* ======================= state ======================= */
    public boolean isRunning() {
        return running;
    }

    public String getError() {
        return error;
    }

    /** 0 = scanning (blue), 1 = reading (amber), 2 = found (green), 3 = check (amber). */
    public void setGuideState(int state) {
        this.guideState = state;
    }

    /** Switches the moving scan line on or off (it only shows while the state is 0 or 1). */
    public void setScanLine(boolean on) {
        this.scanLine = on;
    }

    /** The newest picture with the scanner overlay drawn on it (ARGB, ready to show). */
    public BufferedImage shownFrame() {
        return shownFrame.get();
    }

    /** A private copy of the newest plain picture, for reading. */
    public BufferedImage copyOfFrame() {
        BufferedImage src = rawFrame.get();
        if (src == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** The part of the picture inside the frame. */
    public static BufferedImage cropGuide(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int x = (int) (w * GX);
        int y = (int) (h * GY);
        int cw = Math.min((int) (w * GW), w - x);
        int ch = Math.min((int) (h * GH), h - y);

        BufferedImage out = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src.getSubimage(x, y, cw, ch), 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** A copy of a picture as ARGB, no wider than maxWidth. */
    public static BufferedImage toArgb(BufferedImage src, int maxWidth) {
        double f = src.getWidth() > maxWidth ? (double) maxWidth / src.getWidth() : 1.0;
        int w = Math.max(1, (int) Math.round(src.getWidth() * f));
        int h = Math.max(1, (int) Math.round(src.getHeight() * f));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /* ======================= the scanner overlay ======================= */
    /**
     * Draws the picture with the scanner overlay.
     * animate = true adds the moving scan line (only for states 0 and 1).
     */
    public BufferedImage render(BufferedImage src, int state, boolean animate) {
        double f = Math.min(1.0, PREVIEW_WIDTH / (double) src.getWidth());
        int w = Math.max(1, (int) Math.round(src.getWidth() * f));
        int h = Math.max(1, (int) Math.round(src.getHeight() * f));

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            g.drawImage(src, 0, 0, w, h, null);
            g.drawImage(mask(w, h), 0, 0, null);      // dimmed outside, slightly lighter inside

            double x = w * GX;
            double y = h * GY;
            double gw = w * GW;
            double gh = h * GH;
            double r = radius(w);
            RoundRectangle2D.Double frame = new RoundRectangle2D.Double(x, y, gw, gh, r * 2, r * 2);
            Color color = colorFor(state);

            // thin outline
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(new Color(255, 255, 255, 70));
            g.draw(frame);

            // moving scan line
            if (animate && (state == 0 || state == 1)) {
                drawScanLine(g, frame, x, y, gw, gh, r, color);
            }

            // thick rounded corners
            drawCorners(g, x, y, gw, gh, r, color, Math.max(4f, w / 150f));
        } finally {
            g.dispose();
        }
        return out;
    }

    private static double radius(int w) {
        return Math.max(16, w / 24.0);
    }

    private static Color colorFor(int state) {
        return switch (state) {
            case 1, 3 -> new Color(245, 158, 11);     // amber
            case 2 -> new Color(52, 211, 153);        // green
            default -> new Color(37, 118, 235);       // blue
        };
    }

    private synchronized BufferedImage mask(int w, int h) {
        if (maskImage == null || maskW != w || maskH != h) {
            BufferedImage m = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = m.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double r = radius(w);
                RoundRectangle2D.Double frame =
                        new RoundRectangle2D.Double(w * GX, h * GY, w * GW, h * GH, r * 2, r * 2);

                g.setColor(new Color(0, 0, 0, 135));
                g.fillRect(0, 0, w, h);

                g.setComposite(AlphaComposite.Clear);          // cut the frame out of the dim layer
                g.fill(frame);

                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(new Color(255, 255, 255, 16));
                g.fill(frame);
            } finally {
                g.dispose();
            }
            maskImage = m;
            maskW = w;
            maskH = h;
        }
        return maskImage;
    }

    private void drawScanLine(Graphics2D g, Shape clip, double x, double y, double gw, double gh,
                              double r, Color c) {
        double t = (System.currentTimeMillis() % 2400L) / 2400.0;
        double p = t < 0.5 ? t * 2 : 2 - t * 2;          // 0 -> 1 -> 0
        p = p * p * (3 - 2 * p);                          // smooth ends

        double top = y + r * 0.45;
        double bottom = y + gh - r * 0.45;
        double ly = top + p * (bottom - top);

        Shape old = g.getClip();
        g.setClip(clip);

        g.setPaint(new GradientPaint(0f, (float) (ly - 46), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0),
                0f, (float) ly, new Color(c.getRed(), c.getGreen(), c.getBlue(), 80)));
        g.fill(new Rectangle2D.Double(x, ly - 46, gw, 46));

        g.setPaint(c);
        g.fill(new Rectangle2D.Double(x + r * 0.5, ly - 1.5, gw - r, 3));

        g.setClip(old);
    }

    private void drawCorners(Graphics2D g, double x, double y, double gw, double gh, double r,
                             Color c, float thickness) {
        double len = r * 2.2;
        Path2D.Double p = new Path2D.Double();

        // top left
        p.moveTo(x, y + len);
        p.lineTo(x, y + r);
        p.quadTo(x, y, x + r, y);
        p.lineTo(x + len, y);

        // top right
        p.moveTo(x + gw - len, y);
        p.lineTo(x + gw - r, y);
        p.quadTo(x + gw, y, x + gw, y + r);
        p.lineTo(x + gw, y + len);

        // bottom right
        p.moveTo(x + gw, y + gh - len);
        p.lineTo(x + gw, y + gh - r);
        p.quadTo(x + gw, y + gh, x + gw - r, y + gh);
        p.lineTo(x + gw - len, y + gh);

        // bottom left
        p.moveTo(x + len, y + gh);
        p.lineTo(x + r, y + gh);
        p.quadTo(x, y + gh, x, y + gh - r);
        p.lineTo(x, y + gh - len);

        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(c);
        g.draw(p);
    }
}