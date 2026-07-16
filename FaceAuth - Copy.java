import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.File;

public class FaceAuth {

    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    private static final String CASCADE_FILE = "haarcascade_frontalface_default.xml"; // <-- use absolute path if needed
    private static final int FACE_W = 200;
    private static final int FACE_H = 200;
    private static final double MSE_THRESHOLD = 4000.0;       // relaxed for debug
    private static final double CORRELATION_THRESHOLD = 0.60; // relaxed for debug
    private static final String REGISTERED_FACE_PATH = "registered_face.png";

    private static CascadeClassifier loadCascade() {
        CascadeClassifier detector = new CascadeClassifier(CASCADE_FILE);
        if (detector.empty()) {
            System.err.println("❌ Failed to load cascade from: " + CASCADE_FILE);
        } else {
            System.out.println("✅ Cascade loaded.");
        }
        return detector;
    }

    public static boolean registerFace(String filePath) {
        CascadeClassifier detector = loadCascade();
        if (detector.empty()) return false;

        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("❌ Camera not available!");
            return false;
        }

        Mat frame = new Mat();
        Mat faceToSave = null;
        long start = System.currentTimeMillis();

        System.out.println("📸 Look at the camera – capturing for 6 seconds...");
        while (System.currentTimeMillis() - start < 6000) {
            if (!camera.read(frame)) { 
                // small sleep to avoid tight loop
                try { Thread.sleep(50); } catch (InterruptedException ignored){}
                continue;
            }
            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            detector.detectMultiScale(gray, faces, 1.1, 4, 0, new Size(80,80), new Size()); // min size added
            Rect[] rects = faces.toArray();
            System.out.println("Detected faces: " + rects.length);
            if (rects.length > 0) {
                // choose largest
                Rect best = rects[0];
                for (Rect r : rects) if (r.area() > best.area()) best = r;

                // pad/limit rect to be inside frame
                Rect safe = new Rect(
                    Math.max(best.x, 0),
                    Math.max(best.y, 0),
                    Math.min(best.width, gray.cols() - best.x),
                    Math.min(best.height, gray.rows() - best.y)
                );

                Mat faceROI = new Mat(gray, safe);
                Mat resized = new Mat();
                Imgproc.resize(faceROI, resized, new Size(FACE_W, FACE_H), 0, 0, Imgproc.INTER_CUBIC);
                // use light blur (or none)
                Imgproc.GaussianBlur(resized, resized, new Size(1,1), 0);
                // normalize contrast to reduce lighting differences
                Core.normalize(resized, resized, 0, 255, Core.NORM_MINMAX);
                faceToSave = resized.clone();
                // Optionally break earlier if you want first good frame
            }
        }
        camera.release();

        if (faceToSave != null) {
            boolean ok = Imgcodecs.imwrite(filePath, faceToSave);
            System.out.println(ok ? "✅ Face registered successfully!" : "❌ Failed to save face image.");
            return ok;
        } else {
            System.out.println("❌ No face detected during registration.");
            return false;
        }
    }

    public static boolean verifyFace(String filePath, int timeoutMillis) {
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.println("❌ No registered face found.");
            return false;
        }

        Mat saved = Imgcodecs.imread(filePath, Imgcodecs.IMREAD_GRAYSCALE);
        if (saved.empty()) {
            System.out.println("❌ Invalid registered face image.");
            return false;
        }

        CascadeClassifier detector = loadCascade();
        if (detector.empty()) return false;

        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("❌ Camera not available!");
            return false;
        }

        Mat frame = new Mat();
        long start = System.currentTimeMillis();
        boolean matched = false;

        System.out.println("🧠 Verifying your face...");
        while (System.currentTimeMillis() - start < timeoutMillis) {
            if (!camera.read(frame)) {
                try { Thread.sleep(30);} catch (InterruptedException ignored){}
                continue;
            }
            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            detector.detectMultiScale(gray, faces, 1.1, 4, 0, new Size(80,80), new Size());
            Rect[] rects = faces.toArray();
            if (rects.length > 0) {
                Rect best = rects[0];
                for (Rect r : rects) if (r.area() > best.area()) best = r;

                Rect safe = new Rect(
                    Math.max(best.x, 0),
                    Math.max(best.y, 0),
                    Math.min(best.width, gray.cols() - best.x),
                    Math.min(best.height, gray.rows() - best.y)
                );

                Mat faceROI = new Mat(gray, safe);
                Mat resized = new Mat();
                Imgproc.resize(faceROI, resized, new Size(FACE_W, FACE_H), 0, 0, Imgproc.INTER_CUBIC);
                Imgproc.GaussianBlur(resized, resized, new Size(1,1), 0);
                Core.normalize(resized, resized, 0, 255, Core.NORM_MINMAX);

                double mse = meanSquaredError(saved, resized);
                double corr = correlation(saved, resized);
                System.out.printf("📊 MSE = %.2f | Corr = %.2f%n", mse, corr);

                if (mse < MSE_THRESHOLD && corr > CORRELATION_THRESHOLD) {
                    matched = true;
                    break;
                }
            } else {
                // no faces found — helpful debug info
                System.out.println("No face detected in frame.");
            }
        }

        camera.release();
        System.out.println(matched ? "✅ Face verified successfully!" : "❌ Face not recognized!");
        return matched;
    }

    private static double meanSquaredError(Mat a, Mat b) {
        if (a.rows() != b.rows() || a.cols() != b.cols()) return Double.MAX_VALUE;
        Mat diff = new Mat();
        Core.absdiff(a, b, diff);
        diff.convertTo(diff, CvType.CV_32F);
        Core.multiply(diff, diff, diff);
        Scalar s = Core.sumElems(diff);
        return s.val[0] / (a.rows() * a.cols());
    }

    private static double correlation(Mat a, Mat b) {
        // ensure float and same size
        Mat a32 = new Mat(), b32 = new Mat();
        a.convertTo(a32, CvType.CV_32F);
        b.convertTo(b32, CvType.CV_32F);

        // matchTemplate expects template <= source. We'll call it both ways and take the max.
        Mat out1 = new Mat(), out2 = new Mat();
        Imgproc.matchTemplate(a32, b32, out1, Imgproc.TM_CCOEFF_NORMED); // a vs b
        Imgproc.matchTemplate(b32, a32, out2, Imgproc.TM_CCOEFF_NORMED); // b vs a

        double v1 = out1.get(0,0)[0];
        double v2 = out2.get(0,0)[0];
        return Math.max(v1, v2);
    }

    public static boolean authenticateFace() {
        File f = new File(REGISTERED_FACE_PATH);
        if (!f.exists()) {
            System.out.println("🧍 No face found – registering new face.");
            return registerFace(REGISTERED_FACE_PATH);
        } else {
            return verifyFace(REGISTERED_FACE_PATH, 8000);
        }
    }

    public static boolean reRegisterFace() {
        File old = new File(REGISTERED_FACE_PATH);
        if (old.exists()) old.delete();
        System.out.println("♻️ Re-registering new face...");
        return registerFace(REGISTERED_FACE_PATH);
    }
}
