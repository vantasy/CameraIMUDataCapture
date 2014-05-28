package com.example.datacapture.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.content.res.Configuration;
import android.widget.TextView;

import java.io.IOException;

/**
 * Created by vanta_000 on 2014/4/14.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private static final String TAG = "MyActivity";



    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
//        // empty. Take care of releasing the Camera preview in your activity.
//        mCamera.stopPreview();
//        mCamera.release();
//        mCamera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();

        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

            if (null != mCamera)
            {
                try {
                        Camera.Parameters parameters = mCamera.getParameters();
                        Camera.Size csize = mCamera.getParameters().getPreviewSize();

                        parameters.setPreviewSize(csize.width,csize.height);

                    if (this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                    {
                        parameters.set("orientation", "portrait");
                        parameters.set("rotation", 90);
                        mCamera.setDisplayOrientation(90);

                    } else
                    {
                        parameters.set("orientation", "landscape");
                        mCamera.setDisplayOrientation(0);
                    }

                    mCamera.setParameters(parameters);


                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

        // start preview with new settings
        try {

            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }
}

