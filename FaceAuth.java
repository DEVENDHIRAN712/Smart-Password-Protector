import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.imgcodecs.Imgcodecs;
import java.io.File;

public class FaceAuth {
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    private static final String CASCADE_FILE = "haarcascade_frontalface_default.xml";
    private static final String REGISTERED_FACE_PATH = "registered_face.png";
    private static final int FACE_W = 200, FACE_H = 200;
    private static final double MSE_THRESHOLD = 3500.0;
    private static final double CORR_THRESHOLD = 0.65;

    private static CascadeClassifier loadCascade() {
        CascadeClassifier detector = new CascadeClassifier(CASCADE_FILE);
        if (detector.empty())
            System.err.println("❌ Failed to load cascade: " + CASCADE_FILE);
        else
            System.out.println("✅ Haar cascade loaded.");
        return detector;
    }

    public static boolean registerFace(String path) {
        CascadeClassifier detector = loadCascade();
        if (detector.empty()) return false;

        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("❌ Camera not available!");
            return false;
        }

        System.out.println("📸 Please look straight at the camera. Stay still for a few seconds...");
        Mat frame = new Mat();
        Mat bestFace = null;
        double bestArea = 0;
        long start = System.currentTimeMillis();

        // Let camera warm up
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        while (System.currentTimeMillis() - start < 8000) { // capture for 8 sec
            if (!camera.read(frame)) continue;

            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            detector.detectMultiScale(gray, faces, 1.1, 4, 0,
                    new Size(100, 100), new Size(400, 400));

            for (Rect rect : faces.toArray()) {
                Imgproc.rectangle(frame, rect, new Scalar(0, 255, 0), 2);
                double area = rect.area();
                if (area > bestArea) {
                    bestArea = area;

                    Rect safe = new Rect(
                            Math.max(rect.x, 0),
                            Math.max(rect.y, 0),
                            Math.min(rect.width, gray.cols() - rect.x),
                            Math.min(rect.height, gray.rows() - rect.y)
                    );

                    Mat face = new Mat(gray, safe);
                    Mat resized = new Mat();
                    Imgproc.resize(face, resized, new Size(FACE_W, FACE_H));
                    Core.normalize(resized, resized, 0, 255, Core.NORM_MINMAX);
                    bestFace = resized.clone();
                }
            }

            // Show live window
            Imgproc.putText(frame, "Capturing face... " +
                    (8 - (System.currentTimeMillis() - start) / 1000) + "s",
                    new Point(20, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8,
                    new Scalar(255, 255, 255), 2);

            HighGui.imshow("Face Registration", frame);
            if (HighGui.waitKey(30) == 27) break; // ESC to exit early
        }

        camera.release();
        HighGui.destroyAllWindows();

        if (bestFace != null) {
            boolean ok = Imgcodecs.imwrite(path, bestFace);
            System.out.println(ok ? "✅ Face registered successfully!" : "❌ Failed to save face image.");
            return ok;
        } else {
            System.out.println("❌ No clear face detected during registration.");
            return false;
        }
    }

    public static boolean verifyFace(String path, int timeoutMs) {
        File f = new File(path);
        if (!f.exists()) {
            System.out.println("❌ No registered face image found.");
            return false;
        }

        Mat saved = Imgcodecs.imread(path, Imgcodecs.IMREAD_GRAYSCALE);
        if (saved.empty()) return false;

        CascadeClassifier detector = loadCascade();
        if (detector.empty()) return false;

        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.out.println("❌ Camera not available!");
            return false;
        }

        System.out.println("🧠 Verifying face... Please look at camera.");
        Mat frame = new Mat();
        boolean matched = false;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!camera.read(frame)) continue;

            Mat gray = new Mat();
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(gray, gray);

            MatOfRect faces = new MatOfRect();
            detector.detectMultiScale(gray, faces, 1.1, 4, 0,
                    new Size(100, 100), new Size(400, 400));

            for (Rect rect : faces.toArray()) {
                Imgproc.rectangle(frame, rect, new Scalar(255, 0, 0), 2);
                Mat face = new Mat(gray, rect);
                Mat resized = new Mat();
                Imgproc.resize(face, resized, new Size(FACE_W, FACE_H));
                Core.normalize(resized, resized, 0, 255, Core.NORM_MINMAX);

                double mse = meanSquaredError(saved, resized);
                double corr = correlation(saved, resized);
                Imgproc.putText(frame, String.format("MSE: %.0f Corr: %.2f", mse, corr),
                        new Point(20, 30), Imgproc.FONT_HERSHEY_SIMPLEX, 0.7,
                        new Scalar(255, 255, 255), 2);

                if (mse < MSE_THRESHOLD && corr > CORR_THRESHOLD) {
                    matched = true;
                    break;
                }
            }

            HighGui.imshow("Face Verification", frame);
            if (HighGui.waitKey(30) == 27 || matched) break;
        }

        camera.release();
        HighGui.destroyAllWindows();

        System.out.println(matched ? "✅ Face verified successfully!" : "❌ Face not recognized.");
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
        Mat a32 = new Mat(), b32 = new Mat();
        a.convertTo(a32, CvType.CV_32F);
        b.convertTo(b32, CvType.CV_32F);
        Mat result = new Mat();
        Imgproc.matchTemplate(a32, b32, result, Imgproc.TM_CCOEFF_NORMED);
        return result.get(0, 0)[0];
    }

    public static boolean authenticateFace() {
        File f = new File(REGISTERED_FACE_PATH);
        if (!f.exists()) {
            System.out.println("🧍 No face found – registering new face...");
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
