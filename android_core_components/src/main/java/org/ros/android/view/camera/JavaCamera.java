package org.ros.android.view.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;

import java.util.ArrayList;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_OFF;
import static android.hardware.camera2.CameraMetadata.CONTROL_CAPTURE_INTENT_MOTION_TRACKING;
import static android.hardware.camera2.CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JavaCamera implements NodeMain {
    private static final String TAG = JavaCamera.class.getSimpleName();
    private HandlerThread threadHandler;
    private Handler cameraHandler;
    private String cameraID = "0";
    private CameraDevice camera;
    private CaptureRequest.Builder captureBuilder;
    private List<ImageReader> imageReaders = new ArrayList<>();
    private CameraManager cameraManager;
    private CameraDevice.StateCallback openCallback;
    public static CompressedImagePublisher2 compressedImagePublisher;

    /**
     * 需要在open前调用，否则添加的reader无效，需要重启
     *
     * @param imageReader
     */
    public void addImageReader(ImageReader imageReader) {
        imageReaders.add(imageReader);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean open(Context context, CameraDevice.StateCallback callback) {
        openCallback = callback;
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            // check permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            initLooper();
            // start up Camera (not the recording)
            cameraManager.openCamera(cameraID, cameraDeviceStateCallback, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void close() {
        camera.close();
        threadHandler.quitSafely();
    }

    /**
     * Starting separate thread to handle camera input
     */
    private void initLooper() {
        threadHandler = new HandlerThread("Camera2Thread");
        threadHandler.start();
        cameraHandler = new Handler(threadHandler.getLooper());
    }

    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                //if this session is no longer active, either because the session was explicitly closed
                // , a new session has been created or the camera device has been closed.
                session.setRepeatingRequest(captureBuilder.build(), null, cameraHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                //CameraDevice was already closed
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);

        }
    };

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            try {
                camera = cameraDevice;

                startCameraView(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            if (openCallback != null) {
                openCallback.onOpened(cameraDevice);
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (openCallback != null) {
                openCallback.onDisconnected(camera);
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (openCallback != null) {
                openCallback.onError(camera, error);
            }
        }
    };

    /**
     * starts CameraView
     */
    private void startCameraView(CameraDevice camera) throws CameraAccessException {
        try {
            // to set request for CameraView
            captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        // 不自动对焦,自动对焦会影响相机内参
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
        // 自动曝光
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
//        captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 2.0f);
        // 固定iso
//        captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,);

        // 自动白平衡
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
        captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 3f);
        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, 10000000L);
        //output Surface
        List<Surface> outputSurfaces = new ArrayList<>();

        // 将imageReaders全部放入渲染
        for (ImageReader imageReader : imageReaders) {
            captureBuilder.addTarget(imageReader.getSurface());
            outputSurfaces.add(imageReader.getSurface());
        }

        camera.createCaptureSession(outputSurfaces, sessionStateCallback, cameraHandler);
    }

    public CameraCharacteristics getCameraCharacteristics() throws CameraAccessException {
        final String cameraId = camera.getId();
        return cameraManager.getCameraCharacteristics(cameraId);
    }


    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("ros_camera_preview_view");
    }


    @Override
    public void onStart(ConnectedNode connectedNode) {
        compressedImagePublisher = new CompressedImagePublisher2(connectedNode);
    }


    public static void onNewImage(Bitmap image) {
        if (compressedImagePublisher != null)
            compressedImagePublisher.onNewRawImage(image);
    }

    @Override
    public void onShutdown(Node node) {
    }

    @Override
    public void onShutdownComplete(Node node) {
    }

    @Override
    public void onError(Node node, Throwable throwable) {
    }
}
