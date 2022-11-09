package com.luxand.livenessrecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextPaint;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.luxand.FSDK;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements OnClickListener, ButtonEvents {

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private boolean mIsFailed = false;
    private FrameLayout mLayout;
    private Preview mPreview;
    private ProcessImageAndDrawResults mDraw;
    private final String database = "memory70.dat";

    private boolean wasStopped = false;

    public static float sDensity = 1.0f;

    View buttons;
    
    public void showErrorAndClose(String error, int code) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(error + ": " + code)
            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            })
            .show();        
    }
    
    public void showMessage(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                }
            })
            .setCancelable(false) // cancel with button only
            .show();        
    }
    
    private void resetTrackerParameters() {
        int[] err_pos = new int[1];
        FSDK.SetTrackerMultipleParameters(mDraw.mTracker,
                "RecognizeFaces=true;" +
                        "DetectFacialFeatures=true;" +
                        "HandleArbitraryRotations=true;" +
                        "DetermineFaceRotationAngle=true;" +
                        "InternalResizeWidth=64;" +
                        "FaceDetectionThreshold=9;" +
                        "DetectGender=true;" +
                        "DetectAge=true;" +
                        "DetectExpression=true;" +
                        "DetectAngles=true;", err_pos);
        if (err_pos[0] != 0) {
            showErrorAndClose("Error setting tracker parameters, position", err_pos[0]);
        }
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sDensity = getResources().getDisplayMetrics().scaledDensity;
        
        int res = FSDK.ActivateLibrary("fUJ1M81Wce5x2P7BTwEUNl5Xof5VL9aHP4FwVHfhGRFTv/bIuwBDP2dsIz7jZgTrKBEWSDRhtd5Fa5+oM3Nme7NCCePdmoqleBk9ETP6ZoikUmOnUZ1WEPEDuBLu2iTmswByKwiHpjn5fpkJit/5OfrLRBbfR5Lr58oqMYXUldw=");
        if (res != FSDK.FSDKE_OK) {
            mIsFailed = true;
            showErrorAndClose("FaceSDK activation failed", res);
        } else {
            FSDK.Initialize();
            
            // Hide the window title (it is done in manifest too)
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            
            // Lock orientation
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            mLayout = new FrameLayout(this);
            LayoutParams params = new LayoutParams
                    (LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            mLayout.setLayoutParams(params);
            setContentView(mLayout);

            checkCameraPermissionsAndOpenCamera();
        }                
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            openCamera();
        }
    }

    private void checkCameraPermissionsAndOpenCamera() {
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {

                final Runnable onCloseAlert = new Runnable() {
                    @Override
                    public void run() {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[] {Manifest.permission.CAMERA},
                                CAMERA_PERMISSION_REQUEST_CODE);
                    }
                };

                alert(this, onCloseAlert, "The application processes frames from camera.");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[] {Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_REQUEST_CODE);
            }
        } else {
            openCamera();
        }
    }

    public static void alert(final Context context, final Runnable callback, String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setMessage(message);
        dialog.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        if (callback != null) {
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    callback.run();
                }
            });
        }
        dialog.show();
    }

    private void openCamera() {
        // Camera layer and drawing layer
        View background = new View(this);
        background.setBackgroundColor(Color.BLACK);
        mDraw = new ProcessImageAndDrawResults(this, this);
        mPreview = new Preview(this, mDraw);
        //mPreview.setBackgroundColor(Color.GREEN);
        //mDraw.setBackgroundColor(Color.RED);
        mDraw.mTracker = new FSDK.HTracker();
        String templatePath = this.getApplicationInfo().dataDir + "/" + database;
        if (FSDK.FSDKE_OK != FSDK.LoadTrackerMemoryFromFile(mDraw.mTracker, templatePath)) {
            int res = FSDK.CreateTracker(mDraw.mTracker);
            if (FSDK.FSDKE_OK != res) {
                showErrorAndClose("Error creating tracker", res);
            }
        }

        resetTrackerParameters();

        this.getWindow().setBackgroundDrawable(new ColorDrawable()); //black background

        mLayout.setVisibility(View.VISIBLE);
        addContentView(background, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addContentView(mPreview, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)); //creates MainActivity contents
        addContentView(mDraw, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));


        // Menu
        LayoutInflater inflater = (LayoutInflater)this.getSystemService( Context.LAYOUT_INFLATER_SERVICE );

        if (inflater != null) {
            /*@SuppressLint("InflateParams")*/ buttons = inflater.inflate(R.layout.bottom_menu, null);
            buttons.findViewById(R.id.helpButton).setEnabled(false);
            //buttons.findViewById(R.id.helpButton).setOnClickListener(this);
            buttons.findViewById(R.id.clearButton).setOnClickListener(this);
            addContentView(buttons, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.helpButton) {
            showMessage("Luxand Liveness Recognition\n\nJust tap any detected face to check liveness. " +
                        "The app will prompt you with certain commands to check for liveness." +
                        "For best results, hold the device at arm's length. " +
                        "The SDK is available for mobile developers: www.luxand.com/facesdk"
            );
        } else if (view.getId() == R.id.clearButton) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Are you sure to clear the memory?" )
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialogInterface, int j) {
                        pauseProcessingFrames();
                        FSDK.ClearTracker(mDraw.mTracker);
                        resetTrackerParameters();
                        resumeProcessingFrames();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialogInterface, int j) {
                    }
                })
                .setCancelable(false) // cancel with button only
                .show();
        }
    }

    @Override
    protected void onStop() {
        if (mDraw != null || mPreview != null) {
            mPreview.setVisibility(View.GONE); // to destroy surface
            mLayout.setVisibility(View.GONE);
            mLayout.removeAllViews();
            mPreview.releaseCallbacks();
            mPreview = null;
            mDraw = null;
            wasStopped = true;
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (wasStopped && mDraw == null) {
            checkCameraPermissionsAndOpenCamera();
            //openCamera();
            wasStopped = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDraw != null) {
            pauseProcessingFrames();
            String templatePath = this.getApplicationInfo().dataDir + "/" + database;
            FSDK.SaveTrackerMemoryToFile(mDraw.mTracker, templatePath);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mIsFailed)
            return;
        resumeProcessingFrames();
    }
    
    private void pauseProcessingFrames() {
        if (mDraw != null) {
            mDraw.mStopping = 1;

            // It is essential to limit wait time, because mStopped will not be set to 0, if no frames are feeded to mDraw
            for (int i = 0; i < 100; ++i) {
                if (mDraw.mStopped != 0) break;
                try {
                    Thread.sleep(10);
                } catch (Exception ignored) {
                }
            }
        }
    }
    
    private void resumeProcessingFrames() {
        if (mDraw != null) {
            mDraw.mStopped = 0;
            mDraw.mStopping = 0;
        }
    }

    @Override
    public void enableSubmit() {
        buttons.findViewById(R.id.helpButton).setEnabled(true);
        buttons.findViewById(R.id.helpButton).setOnClickListener(view -> {
            String galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()
                    + "/first_frame" + System.currentTimeMillis() + ".jpg";
            mPreview.mCamera.takePicture(null, null, (bytes, camera) -> {
                try {
                    FileOutputStream fos = new FileOutputStream(galleryPath);
                    fos.write(bytes);
                    fos.close();
                } catch (IOException e) {
                    Toast.makeText(getBaseContext(), "Not found", Toast.LENGTH_LONG).show();
                }

                String email = ((EditText) buttons.findViewById(R.id.email)).getText().toString();
                Bitmap bm = BitmapFactory.decodeFile(galleryPath);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); // bm is the bitmap object
                byte[] b = baos.toByteArray();
                String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
                Log.d("imagestuff", encodedImage);
                ProgressDialog pg = new ProgressDialog(MainActivity.this);
                pg.setMessage("Loading");
                pg.show();
                mPreview.mCamera.startPreview();
                buttons.findViewById(R.id.helpButton).setEnabled(false);
                //disable button when no face is detected
                //change camera to camera 2 or X
                //change options for detecting liveness(smile, left right)

                new Thread(() -> ApiKt.getApiService().getFaceMatch(new VerifyBody(email.trim(), encodedImage)).enqueue(new Callback<Object>() {
                    @Override
                    public void onResponse(@NonNull Call<Object> call, @NonNull Response<Object> response) {
                        //Log.d("response", response.body().toString());
                        if (response.isSuccessful()) {
                            try {
                                showDialog(new JSONObject(response.body().toString()).getString("message"));
                            } catch (JSONException e) {
                                showDialog(e.getMessage());
                                e.printStackTrace();
                            }
                        } else  {
                            try {
                                showDialog(new JSONObject(response.errorBody().string()).getString("message"));
                            } catch (IOException | JSONException | NullPointerException e) {
                                showDialog("Something went wrong");
                                e.printStackTrace();
                            }
                        }
                        runOnUiThread(pg::dismiss);
                    }

                    @Override
                    public void onFailure(Call<Object> call, Throwable t) {
                        showDialog(t.getMessage());
                        runOnUiThread(pg::dismiss);
                    }
                })).start();

            });
        });
    }


    public void showDialog(String message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        dialog.setMessage(message);
        dialog.setPositiveButton("Ok", (dialog1, which) -> dialog1.dismiss());
        dialog.show();
    }
}



class FaceRectangle {

    public float x1, y1, x2, y2;

    public void set(float w, float h) {
        x1 = -w / 2;
        y1 = -h / 2;
        x2 =  w / 2;
        y2 =  h / 2;
    }
}

class LowPassFilter {

    private float a;
    private Float y = null;

    public LowPassFilter() {
        a = 0.325f;
    }

    public float pass(final float x) {
        y = a * x + (1 - a) * (y == null ? x : y);
        return y;
    }

}

class Point {

    public float x;
    public float y;

    public Point() {
        x = 0;
        y = 0;
    }

}

interface ButtonEvents {
    void enableSubmit();
}

