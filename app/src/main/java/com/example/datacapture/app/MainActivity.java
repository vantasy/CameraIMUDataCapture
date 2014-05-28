package com.example.datacapture.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.text.SimpleDateFormat;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Message;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;

import android.graphics.PixelFormat;
import android.support.v7.app.ActionBarActivity;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import android.widget.Button;
import android.widget.FrameLayout;

public class MainActivity extends ActionBarActivity {

    // ------------------------------

    private Camera mCamera;
    private CameraPreview mPreview;
    private SensorMonitor acc;

    // ---------- 参数设置 ----------

    public static final int maxImageNum      = 50;      // 最多采集图像数量
    public static final int captureFPS       = 1000;    // 每多少秒采集一张图片
    public static final int sensorCaptureFPS = 50;      // 每多少秒采集一次传感器数据

    private int imgWidth  = 1280;
    private int imgHeight = 960;

    // ---------- 布尔变量 ----------

    public boolean isCapturing          = false;    // 记录相机是否在进行采集
    public boolean isSensorCapturing    = false;    // 记录传感器是否在进行采集

    // ---------- 显示元素 ----------

    private Button buttonCapture, buttonStop, buttonSensorCapture, buttonSensorStop;
    private Button buttonCameraCapture, buttonCameraStop;
    public TextView accView;
    public TextView gyroView;
    public TextView cameraStatus;
    FrameLayout preview;

    // ------- 句柄以及定时器 -------

    private Handler handler;
    private Timer timerCaptureFPS;  // 控制采集帧率定时器
    private Timer timerStop;        // 控制停止定时器

    // ------- 计数器与时间戳 -------

    public int imageNum = 0;
    private long timestamp;
    private long startTimestamp;
    private long cameraCaptureStartTimestamp;
    private long cameraCaptureFinishTimestamp;
    private long onShutterTimestamp;

    // ------- 文件 ----------------

    private File imageInfoData;
    private FileOutputStream imgInfoFOS;

    private static File dataDir;



    // ---------- 其他变量 ----------

    private static final String TAG = "MyActivity";
//    private static final double EPSILON = 0.1f;
//    private static final float NS2S = 1.0f / 1000000000.0f;
//    private final float[] deltaRotationVector = new float[4];


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonCapture       = (Button) findViewById(R.id.button_capture);
        buttonStop          = (Button) findViewById(R.id.button_stop);
        buttonCameraCapture = (Button) findViewById(R.id.button_camera_capture);
        buttonCameraStop    = (Button) findViewById(R.id.button_camera_stop);
        buttonSensorCapture = (Button) findViewById(R.id.button_sensor_capture);
        buttonSensorStop    = (Button) findViewById(R.id.button_sensor_stop);

        accView             = (TextView) findViewById(R.id.acc_xcoor);
        gyroView            = (TextView) findViewById(R.id.gyro_xcoor);
        cameraStatus        = (TextView) findViewById(R.id.camera_status);

        preview = (FrameLayout)findViewById(R.id.camera_preview);


        // Create的时候，首先让stop button不可用
        buttonStop.setEnabled(false);
        buttonSensorStop.setEnabled(false);
        buttonCameraStop.setEnabled(false);

        buttonCapture.setOnClickListener(new OnButtonClick());
        buttonStop.setOnClickListener(new OnButtonClick());
        buttonCameraCapture.setOnClickListener(new OnButtonClick());
        buttonCameraStop.setOnClickListener(new OnButtonClick());
        buttonSensorCapture.setOnClickListener(new OnButtonClick());
        buttonSensorStop.setOnClickListener(new OnButtonClick());

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if(msg.what == 0x123){
                    takeOneShot();
                    imageNum++;
                }else if(msg.what == 0x124){
                    stop();
                }
            }
        };



    }

    public class OnButtonClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId())
            {
                case R.id.button_capture:

                    init();

                    startTimestamp = System.currentTimeMillis();

                    timerStop = new Timer();
                    timerStop.schedule(new stopThread(),new Date(),500);

                    timerCaptureFPS = new Timer();
                    timerCaptureFPS.schedule(new captureThread(), 1000, captureFPS);

                    acc = new SensorMonitor(v.getContext(),accView,gyroView,startTimestamp,dataDir);
                    isSensorCapturing = true;
                    buttonSensorStop.setEnabled(true);

                    break;

                case R.id.button_stop:

                    stop();
                    isSensorCapturing = false;
                    acc.releaseSensor();

                    break;

                case R.id.button_camera_capture:

                    init();

                    startTimestamp = System.currentTimeMillis();

                    timerStop = new Timer();
                    timerStop.schedule(new stopThread(),new Date(),500);

                    timerCaptureFPS = new Timer();
                    timerCaptureFPS.schedule(new captureThread(), 1000, captureFPS);

                    break;

                case R.id.button_camera_stop:

                    stop();
                    break;

                case R.id.button_sensor_capture:

                    acc = new SensorMonitor(v.getContext(),accView,gyroView,0,null);
                    isSensorCapturing = true;
                    buttonSensorStop.setEnabled(true);
                    break;

                case R.id.button_sensor_stop:

                    isSensorCapturing = false;
                    acc.releaseSensor();
                    break;

            }
        }

    }

    class captureThread extends TimerTask{
        @Override
        public void run() {
            handler.sendEmptyMessage(0x123);
        }
    }

    class stopThread extends TimerTask{
        @Override
        public void run(){

            if(imageNum > maxImageNum){
                handler.sendEmptyMessage(0x124);
                this.cancel();
            }
        }
    }


    private void init(){

        mCamera = getCameraInstance();

        // --------- Config Camera --------
        Camera.Parameters parameters = mCamera.getParameters();

        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPictureSize(imgWidth, imgHeight);

        try{
            parameters.setSceneMode(Camera.Parameters.SCENE_MODE_ACTION);
        }
        catch (Exception e){
            Log.d(TAG, "Error starting action mode: " + e.getMessage());
        }

        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);

        mCamera.setParameters(parameters);

        // ------------------

        createDataDir();

        imageInfoData = getImgInfoFile();

        try{
            imgInfoFOS = new FileOutputStream(imageInfoData);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }

        cameraStatus.setText("Camera Status : OK");

        mPreview = new CameraPreview(this, mCamera);
        preview.addView(mPreview);

        buttonStop.setEnabled(true);
        buttonCameraStop.setEnabled(true);

    }

    Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void onShutter() {
            mCamera.enableShutterSound(true);
            onShutterTimestamp = System.currentTimeMillis() - startTimestamp;
        }
    };

    private static void createDataDir(){

        String dataFolder = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        dataDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), dataFolder);

        if (!dataDir.exists()){
            if (!dataDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create image directory");
                }
        }
    }

    private void takeOneShot() {

        buttonCapture.setEnabled(false);
        buttonCameraCapture.setEnabled(false);


        cameraCaptureStartTimestamp = System.currentTimeMillis() - startTimestamp;

        if (mCamera != null && !isCapturing)
        {
            try
            {
                mCamera.startPreview();
            } catch (Exception e){
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }

            isCapturing = true;
            mCamera.takePicture(mShutterCallback, null, mPicture);

        }

        isCapturing = false;

        cameraCaptureFinishTimestamp = System.currentTimeMillis() - startTimestamp;

        long dT = cameraCaptureFinishTimestamp - cameraCaptureStartTimestamp;

        cameraStatus.setText("Camera Status : Image No." + imageNum + "\nCapture lasts for:" + dT + "ms" +"\n onShutterTime:" + onShutterTimestamp);

        try{
            imgInfoFOS.write((imageNum + " " + cameraCaptureStartTimestamp + " " + onShutterTimestamp + " " + cameraCaptureFinishTimestamp + "\n").getBytes());
            imgInfoFOS.flush();
        } catch (IOException e){
            e.printStackTrace();
        }

    }



    private void stop() {

        try{
            imgInfoFOS.close();
        } catch (IOException e){
            e.printStackTrace();
        }

        releaseCamera();
        cameraStatus.setText("End");
        timerCaptureFPS.cancel();

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isSensorCapturing){
            acc.releaseSensor();
        }

        releaseCamera();              // release the camera immediately on pause event
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private PictureCallback mPicture = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile( );

            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: " );
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };



    /** Create a File for saving an image or video */
    private static File getOutputMediaFile( ){

            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            File mediaFile;

                mediaFile = new File(dataDir.getPath() + File.separator +
                        "IMG_"+ timeStamp + ".jpg");

        return mediaFile;
    }


    private void releaseCamera(){
        if (mCamera != null){
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private static File getImgInfoFile(){

        File imageInfoFile;

        imageInfoFile = new File(dataDir.getPath() + File.separator +
                "ImgInfo" + ".txt");

        return imageInfoFile;
    }

}











