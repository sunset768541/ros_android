/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.cmic.myros;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.core.app.ActivityCompat;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.ros.address.InetAddressFactory;
import org.ros.android.Config;
import org.ros.android.RosActivity;
import org.ros.android.view.camera.ImageUtils;
import org.ros.android.view.camera.JavaCamera;
import org.ros.android.view.camera.RosCameraPreviewView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.io.FileOutputStream;

/**
 * @author ethan.rublee@gmail.com (Ethan Rublee)
 * @author damonkohler@google.com (Damon Kohler)
 * @author huaibovip@gmail.com (Charles)
 * 采用了Camera2的API
 */

public class MainActivity2 extends RosActivity {
    private static String TAG = "MainActivity";
    private int cameraId = 0;
    ImuPublisher imu_pub;
    private ImageReader imageReader;
    private JavaCamera javaCamera;
    private NodeMainExecutor nodeMainExecutor;
    private SensorManager mSensorManager;
    private final int imageWidth = 640;
    private final int imageHeight = 480;

    public MainActivity2() {
        super("ROS", "Camera & Imu");
    }
    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireNextImage();

            if (image == null) {
                return;
            }
            Log.i(TAG,"get new image, height: " + image.getHeight() + " width: " + image.getWidth());
            Mat originMat = ImageUtils.getMatFromImage(image);
            final Bitmap originBitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(originMat, originBitmap);
            JavaCamera.onNewImage(originBitmap);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((ImageView) findViewById(R.id.java_camera_view)).setImageBitmap(originBitmap);
                }
            });
            image.close();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);

        imageReader.setOnImageAvailableListener(onImageAvailableListener, null);

        javaCamera = new JavaCamera();
        javaCamera.addImageReader(imageReader);
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
    }


    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        this.nodeMainExecutor = nodeMainExecutor;
        String[] PERMISSIONS = {"", "", "", ""};
        PERMISSIONS[0] = Manifest.permission.ACCESS_FINE_LOCATION;
        PERMISSIONS[1] = Manifest.permission.CAMERA;
        PERMISSIONS[2] = Manifest.permission.READ_EXTERNAL_STORAGE;
        PERMISSIONS[3] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
        NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfiguration3.setMasterUri(getMasterUri());
        nodeConfiguration3.setNodeName("android_sensors_driver_imu");
        this.imu_pub = new ImuPublisher(mSensorManager);
        nodeMainExecutor.execute(this.imu_pub, nodeConfiguration3);
    }


    private void executeCamera() {
        NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfiguration2.setMasterUri(getMasterUri());
        nodeConfiguration2.setNodeName("android_sensors_driver_camera");
        javaCamera.open(this, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
            }

            @Override
            public void onDisconnected(CameraDevice camera) {

            }

            @Override
            public void onError(CameraDevice camera, int error) {

            }
        });
        nodeMainExecutor.execute(javaCamera, nodeConfiguration2);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
//                executeGPS();
            }
            if (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                if (!Config.isOnlyUseImu) {
                    executeCamera();
                }
            }

            if (grantResults.length > 2 && grantResults[2] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[3] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
            }
        }
    }

    static {
        System.loadLibrary("opencv_java3");
    }
}
