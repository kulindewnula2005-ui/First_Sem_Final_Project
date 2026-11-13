package lk.ijse.firstsemfinalproject25.util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Opens a webcam, keeps the newest picture, and draws the "put the plate here" frame on the preview. */
public class CameraService {

    /** Try 1280x720 first (better for reading plates); falls back to 640x480. */
    private static final boolean PREFER_HD = true;

    // the frame in the middle of the picture, as a share of the picture size
    private static final double GX = 0.20;
    private static final double GY = 0.28;
    private static final double GW = 0.60;
    private static final double GH = 0.44;

    private List<Webcam> cameras;
    private Webcam webcam;
    private Thread loop;

    private volatile boolean running;
    private volatile int guideState;        // 0 = searching (red), 1 = reading (amber), 2 = found (green), 3 = check (amber)
    private volatile String error;

    private final AtomicReference<BufferedImage> rawFrame = new AtomicReference<>();
    private final AtomicReference<BufferedImage> shownFrame = new AtomicReference<>();

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
                shownFrame.set(withGuide(img, guideState));
                Thread.sleep(35);
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

    public void setGuideState(int state) {
        this.guideState = state;
    }

    /** The newest picture with the frame drawn on it (ARGB, ready to show). */
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

    /* ======================= the frame on the preview ======================= */
    private BufferedImage withGuide(BufferedImage src, int state) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);

            int x = (int) (w * GX);
            int y = (int) (h * GY);
            int gw = (int) (w * GW);
            int gh = (int) (h * GH);

            // darken everything outside the frame
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(0, 0, w, y);
            g.fillRect(0, y + gh, w, h - y - gh);
            g.fillRect(0, y, x, gh);
            g.fillRect(x + gw, y, w - x - gw, gh);

            Color color;
            switch (state) {
                case 1, 3 -> color = new Color(245, 158, 11);
                case 2 -> color = new Color(52, 211, 153);
                default -> color = new Color(225, 29, 29);
            }
            g.setColor(color);
            int t = Math.max(3, w / 320);
            int len = Math.max(24, w / 14);
            g.fillRect(x, y, len, t);
            g.fillRect(x, y, t, len);
            g.fillRect(x + gw - len, y, len, t);
            g.fillRect(x + gw - t, y, t, len);
            g.fillRect(x, y + gh - t, len, t);
            g.fillRect(x, y + gh - len, t, len);
            g.fillRect(x + gw - len, y + gh - t, len, t);
            g.fillRect(x + gw - t, y + gh - len, t, len);

            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
            g.drawRect(x, y, gw, gh);
        } finally {
            g.dispose();
        }
        return out;
    }
}