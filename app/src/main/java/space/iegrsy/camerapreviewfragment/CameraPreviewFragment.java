package space.iegrsy.camerapreviewfragment;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.List;

import space.iegrsy.encoder.AvcEncoder;
import space.iegrsy.h264encodeapp.DataSender;
import space.iegrsy.h264encodeapp.R;

import static java.lang.Math.ceil;


public class CameraPreviewFragment extends Fragment implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private Camera mCamera;
    private Camera.Size mPreviewSize;
    private byte[] mPreviewBuffer;

    private Context context;
    private SurfaceHolder holder;

    private boolean isPreviewRunning = false;

    private AvcEncoder encoder;

    public CameraPreviewFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_camera_preview, container, false);

        SurfaceView surfaceView = (SurfaceView) rootView.findViewById(R.id.preview_surface);
        holder = surfaceView.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    private DataSender.DataSenderUDP dataSenderUDP;

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        camera.addCallbackBuffer(mPreviewBuffer);
        // TODO: Implement this: [data ==> Frame format YV12]
        if (encoder != null) {
            byte[] encData = encoder.offerEncoder(data);
            if (dataSenderUDP == null)
                dataSenderUDP = new DataSender.DataSenderUDP("10.5.20.41", 7777);
            dataSenderUDP.sendDataUDP(encData);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            startCamera(holder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setupCameraParameters(context, mCamera);
        mCamera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    private void startCamera(SurfaceHolder surfaceHolder) throws Exception {
        if (isPreviewRunning)
            stopCamera();

        mCamera = Camera.open();

        mPreviewSize = setupCameraParameters(context, mCamera);
        mPreviewBuffer = new byte[calculateBufferSize(mPreviewSize.width, mPreviewSize.height)];

        surfaceHolder.setFixedSize(mPreviewSize.width, mPreviewSize.height);

        mCamera.setPreviewDisplay(surfaceHolder);
        mCamera.addCallbackBuffer(mPreviewBuffer);
        mCamera.setPreviewCallbackWithBuffer(this);

        mCamera.startPreview();
        encoder = new AvcEncoder().init(mPreviewSize.width, mPreviewSize.height);

        isPreviewRunning = true;
    }

    private void stopCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        if (encoder != null) {
            encoder.release();
            encoder = null;
        }

        isPreviewRunning = false;
    }

    private static Camera.Size setupCameraParameters(Context context, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size previewSize = previewSizes.get(0); //Max preview size

        parameters.setPreviewFormat(ImageFormat.YV12);
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);

        camera.setParameters(parameters);

        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        if (display.getRotation() == Surface.ROTATION_0) {
            camera.setDisplayOrientation(90);
        } else if (display.getRotation() == Surface.ROTATION_270) {
            camera.setDisplayOrientation(180);
        }

        return previewSize;
    }

    private static int calculateBufferSize(int w, int h) {
        int yStride = (int) ceil(w / 16.0) * 16;
        int uvStride = (int) ceil((yStride / 2.0) / 16.0) * 16;
        int ySize = yStride * h;
        int uvSize = uvStride * h / 2;

        return ySize + uvSize * 2;
    }
}
