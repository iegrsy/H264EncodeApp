package space.iegrsy.h264encodeapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import space.iegrsy.camerapreviewfragment.CameraPreviewFragment;

public class MainActivity extends AppCompatActivity {

    public static final String[] PERMISSION_STRINGS = new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    public static final int REQUEST_CAMERA_PERMISSION = 123;

    private LinearLayout linearLayout;
    private ImageButton connectBtn;

    private CameraPreviewFragment cameraPreviewFragment;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraPreviewFragment = new CameraPreviewFragment();
        setContainer(cameraPreviewFragment);
        ((FrameLayout) findViewById(R.id.main_container)).setOnTouchListener(new UIHelper.OnSwipeTouchListener(this, new UIHelper.OnSwipeTouchListener.SwipeListener() {
            @Override
            public void onSwipeRight() {

            }

            @Override
            public void onSwipeLeft() {

            }

            @Override
            public void onSwipeTop() {
                UIHelper.slideToUp(linearLayout);
            }

            @Override
            public void onSwipeBottom() {
                UIHelper.slideToDown(linearLayout);
            }
        }));
        linearLayout = findViewById(R.id.control_layout);
        connectBtn = (ImageButton) findViewById(R.id.connect_btn);
        connectBtn.setOnClickListener(connectOnClickListener);
    }

    private boolean isConnected = false;

    private View.OnClickListener connectOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            String host = ((TextInputEditText) findViewById(R.id.txt_host)).getText().toString();
            int port = Integer.parseInt(((TextInputEditText) findViewById(R.id.txt_port)).getText().toString());

            if (isConnected)
                cameraPreviewFragment.disconnectServer();
            else
                cameraPreviewFragment.connectToServer(host, port, stateListener);
        }
    };

    private DataSender.DataSenderGRPC.StateListener stateListener = new DataSender.DataSenderGRPC.StateListener() {
        @Override
        public void onState(final boolean isRun) {
            isConnected = isRun;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectBtn.setImageDrawable(isRun ? getDrawable(R.drawable.ic_close_black_24dp) : getDrawable(R.drawable.ic_call_made_black_24dp));
                }
            });
        }
    };

    private void setContainer(Fragment fragment) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.main_container, fragment).commitNow();
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                new ConfirmationDialog().show(getSupportFragmentManager(), "Camera Permission");
            } else {
                requestPermissions(PERMISSION_STRINGS, REQUEST_CAMERA_PERMISSION);
            }
    }

    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage("Need permissions")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(PERMISSION_STRINGS, REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setContainer(new CameraPreviewFragment());
            }
        } else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public static class UIHelper {
        public static void toggleViewLeftRight(View view) {
            if (view.getVisibility() != View.VISIBLE)
                slideToRight(view);
            else
                slideToLeft(view);
        }

        public static void toggleViewUpDown(View view) {
            if (view.getVisibility() != View.VISIBLE)
                slideToUp(view);
            else
                slideToDown(view);
        }

        // To animate view slide out from left to right
        public static void slideToRight(View view) {
            TranslateAnimation animate = new TranslateAnimation(-view.getWidth(), 0, 0, 0);
            animate.setDuration(500);
            animate.setFillAfter(false);
            view.startAnimation(animate);
            view.setVisibility(View.VISIBLE);
        }

        // To animate view slide out from right to left
        public static void slideToLeft(View view) {
            TranslateAnimation animate = new TranslateAnimation(0, -view.getWidth(), 0, 0);
            animate.setDuration(500);
            animate.setFillAfter(false);
            view.startAnimation(animate);
            view.setVisibility(View.GONE);
        }

        // To animate view slide out from left to right
        public static void slideToUp(View view) {
            TranslateAnimation animate = new TranslateAnimation(0, 0, view.getHeight(), 0);
            animate.setDuration(500);
            animate.setFillAfter(false);
            view.startAnimation(animate);
            view.setVisibility(View.VISIBLE);
        }

        // To animate view slide out from right to left
        public static void slideToDown(View view) {
            TranslateAnimation animate = new TranslateAnimation(0, 0, 0, view.getHeight());
            animate.setDuration(500);
            animate.setFillAfter(false);
            view.startAnimation(animate);
            view.setVisibility(View.GONE);
        }

        public static class OnSwipeTouchListener implements View.OnTouchListener {

            private final GestureDetector gestureDetector;

            public OnSwipeTouchListener(Context ctx, SwipeListener swipeListener) {
                gestureDetector = new GestureDetector(ctx, new GestureListener(swipeListener));
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }

            private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
                private static final int SWIPE_THRESHOLD = 100;
                private static final int SWIPE_VELOCITY_THRESHOLD = 100;

                private final SwipeListener swipeListener;

                GestureListener(SwipeListener swipeListener) {
                    this.swipeListener = swipeListener;
                }

                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    boolean result = false;
                    try {
                        float diffY = e2.getY() - e1.getY();
                        float diffX = e2.getX() - e1.getX();
                        if (Math.abs(diffX) > Math.abs(diffY)) {
                            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                                if (diffX > 0) {
                                    if (swipeListener != null) swipeListener.onSwipeRight();
                                } else {
                                    if (swipeListener != null) swipeListener.onSwipeLeft();
                                }
                                result = true;
                            }
                        } else if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffY > 0) {
                                if (swipeListener != null) swipeListener.onSwipeBottom();
                            } else {
                                if (swipeListener != null) swipeListener.onSwipeTop();
                            }
                            result = true;
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                    return result;
                }
            }

            public interface SwipeListener {
                void onSwipeRight();

                void onSwipeLeft();

                void onSwipeTop();

                void onSwipeBottom();
            }
        }
    }
}
