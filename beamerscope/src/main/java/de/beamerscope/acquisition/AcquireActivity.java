package de.beamerscope.acquisition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRouter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.beamerscope.R;
import de.beamerscope.datasets.Dataset;
import de.beamerscope.presentation.MyPresentation;
import de.beamerscope.utils.CreatePatterns;
import de.beamerscope.utils.ImageUtils;

import static de.beamerscope.nativepart.NativePart.qDPC;
import static de.beamerscope.utils.CreatePatterns.generateRanNumVect;
import static de.beamerscope.utils.CreatePatterns.getCircle;
import static de.beamerscope.utils.CreatePatterns.getDPC;
import static de.beamerscope.utils.CreatePatterns.getDarkfield;
import static de.beamerscope.utils.CreatePatterns.getSegments;
import static de.beamerscope.utils.FileUtils.imwriteNorm;
import static org.opencv.core.Core.NORM_MINMAX;
import static org.opencv.core.Core.divide;
import static org.opencv.core.Core.merge;
import static org.opencv.core.Core.normalize;
import static org.opencv.imgcodecs.Imgcodecs.CV_LOAD_IMAGE_ANYDEPTH;
import static org.opencv.imgcodecs.Imgcodecs.imread;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

/**
 * Created by Bene on 26.09.2015.
 */

public class AcquireActivity extends Activity implements AcquireSettings.NoticeDialogListener {



    //*************************************************
    public MediaRouter mMediaRouter;

    // Active Presentation, set to null if no secondary screen is enabled
    public MyPresentation mPresentation;

    public Bitmap mBitmap = null;
    //*************************************************


    // Flag if Background images have been acquired already
    public boolean background_flag = false;

    // global center positions for x/y derived from the calibration routine
    public int global_cx = 0;
    public int global_cy = 0;
    public int global_na = 1;

    public double objectiveNA = 0.1;
    public double maxNA = 1.;
    public double minNA = .01;
    public int global_NA_default = 100;

    public boolean cameraReady = true;
    public int mmCount = 5;
    public float mmDelay = 0.0f;
    public int aecCompensation = 0;
    public String datasetName = "Dataset";
    public boolean usingHDR = false;
    public Dataset mDataset;
    public int isoSetting = 200;


    // (default) global file paths
    String g_DPC_path_BG  = Environment.getExternalStorageDirectory()+"/Beamerscope/DPCMode_Background/";
    String g_DPC_path  = Environment.getExternalStorageDirectory()+"/Beamerscope/DPCMode/";

    String g_BF_path_BG = Environment.getExternalStorageDirectory()+"/Beamerscope/BFMode_Background/";
    String g_BF_path = Environment.getExternalStorageDirectory()+"/Beamerscope/BFMode/";

    String g_MM_path_BG = Environment.getExternalStorageDirectory()+"/Beamerscope/MMMode_Background/";
    String g_MM_path = Environment.getExternalStorageDirectory()+"/Beamerscope/MMMode/";

    String g_DF_path_BG = Environment.getExternalStorageDirectory()+"/Beamerscope/DFMode_Background/";
    String g_DF_path = Environment.getExternalStorageDirectory()+"/Beamerscope/DFMode/";

    String g_FPM_path_BG  = Environment.getExternalStorageDirectory()+"/Beamerscope/FPMMode_Background/";
    String g_FPM_path  = Environment.getExternalStorageDirectory()+"/Beamerscope/FPMMode/";

    String g_Capture_path  = Environment.getExternalStorageDirectory()+"/Beamerscope/Capture/";


    public DialogFragment settingsDialogFragment;
    private File full_image_file;

    // GUI Settings
    private Button btnSetup;
    private Button btnProcess;
    private Button btnCapture;

    private SeekBar zoomSlider;
    private SeekBar exposureSlider;
    private SeekBar naSlider;
    private SeekBar xposSlider;
    private SeekBar yposSlider;

    private TextView zoomValue;
    private TextView exposureValue;
    private TextView naValue;
    private TextView xposValue;
    private TextView yposValue;

    ProgressDialog progressDialog;

    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;

    FrameLayout progressBarHolder;

    // Settings fro input/output
    private int initialZoomValue = 0;
    private int initialExposureValue = 10;
    private int initialXPosValue = 50;
    private int initialYPosValue = 50;

    int progressValueExposure; //progress value of seekbar
    int global_exposuretime = 0;

    // define ouput Data to store result
    float[] TF_result_after_NN = new float[(int) (OUTPUT_SIZE)];

    // Switch if background has to be acquired
    Boolean acquireBackground = false;

    // GUI-Settings
    Button btnAcquire;
    Button btnAcquireBG;
    Button btnShowResult;

    private TextView acquireTextView;
    private TextView acquireTextView2;
    private TextView timeLeftTextView;

    private ProgressBar acquireProgressBar;

    // Different Imaging Modes
    String BFMode = "BFMode";
    String MMMode = "MMMode";
    String FPMMode = "FPMMode";
    String DFMode = "DarkfieldMode";
    String DPCMode = "DPCMode";
    // Different Imaging Modes with Background


    // Default Imaging Method (starts with this one)
    private String acquireType = DPCMode;

    // switch if optimized illumination source has been calculated already
    boolean g_illumination_processed = false;




    //********************************************************************************************** 
    // TENSORFLOW STUFF 
    // ********************************************************************************************** 


    //private static final String MODEL_FILE = "file:///android_asset/expert-graph_2D_128px.pb";
    File extStore = Environment.getExternalStorageDirectory();
    //private final String MODEL_FILE = String.valueOf(extStore)+"/Beamerscope/TF/expert-graph_2D_128px.pb"; //expert-graph_CN.pb
    private final String MODEL_FILE = String.valueOf(extStore)+"/Beamerscope/TF/expert-graph_CN.pb"; //
    //String MODEL_FILE = "file:///android_asset/first-graph.pb"; // name of python protobuffer file 
    String INPUT_NODE = "input";                                // name of python input node 
    String OUTPUT_NODE = "output";                              // name of python output node 
    long[] INPUT_SIZE = {1,128,128,2};                            // Size of input RGB image
    private static final int OUTPUT_SIZE = 48;

    // create Tensorflow Object 
    TensorFlowInferenceInterface inferenceInterface;

    //********************************************************************************************** 




    //**********************************************************************************************
    //  Method OnCreate
    //**********************************************************************************************


    public AcquireActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_acquire);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Load native libraries
        System.loadLibrary("native_microscope");
        System.loadLibrary("tensorflow_inference");


        // BEGIN_INCLUDE(getMediaRouter)
        // Get the MediaRouter service
        mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
        // END_INCLUDE(getMediaRouter)

        // initialize Tensorflow object
        inferenceInterface = new TensorFlowInferenceInterface(getAssets(), MODEL_FILE);

        // get acquire type from overlaying activity
        Bundle b = getIntent().getExtras();
        if (b != null) {
            acquireType = (String) b.get("type");
        }
        Log.i("Selected Acquire Type", acquireType);



        //view.findViewById(R.id.picture).setOnClickListener(this);
        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);

     /*
        1. Zoom using slider (seekbar)
        The seekbar value can change from 0% to 100%
        where 0% corresponds to zoom_level 1x and 100% corresponds to maximum zoom allowed by phone camera
     */

        zoomSlider = (SeekBar) findViewById(R.id.seekBar);
        zoomSlider.setMax(100);
        zoomSlider.setProgress(initialZoomValue);

        zoomValue = (TextView) findViewById(R.id.textViewZoom);
        zoomValue.setText("Zoom: " + zoomSlider.getProgress() + "%");

        zoomSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    int progressValue; //progress value of seekbar

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        progressValue = progress;
                        zoomValue.setText("Zoom: " + progress + "%");
                        slideToZoom(progressValue);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        zoomValue.setText("Zoom: " + progressValue + "%");
                        slideToZoom(progressValue);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        zoomValue.setText("Zoom: " + progressValue + "%");
                        slideToZoom(progressValue);
                    }
                }
        );

        /*
        1. Exposure using slider (seekbar)
        The seekbar value can change from 0% to 100%
        where 0% corresponds to expsorue_level 1x and 100% corresponds to maximum exposure allowed by phone camera
     */

        exposureSlider = (SeekBar) findViewById(R.id.seekBarExp);
        exposureSlider.setMax(250);
        exposureSlider.setProgress(initialExposureValue);

        exposureValue = (TextView) findViewById(R.id.textViewExp);
        exposureValue.setText("Exposuretime: " + initialExposureValue + "ms");

        // Initlialize Progressbar for wait-time while processing data
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);

        exposureSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        progressValueExposure = progress;
                        exposureValue.setText("Exposuretime: " + progress + "ms");
                        slideToExposuretime(progressValueExposure);
                    }


                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        exposureValue.setText("Exposuretime: " + progressValueExposure + "ms");
                        slideToExposuretime(progressValueExposure);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        exposureValue.setText("Exposuretime: " + progressValueExposure + "ms");
                        slideToExposuretime(progressValueExposure);
                    }
                }
        );

        
         /*
        3. Adjust NA using slider (seekbar)
        The seekbar value can change from 0% to 100%
        where 0% corresponds to NA=0.01 and 100% corresponds to maximum NA allowed by phone camera
        */

        naSlider = (SeekBar) findViewById(R.id.seekBarNA);
        naSlider.setMax(100);
        naSlider.setProgress(global_NA_default);

        naValue = (TextView) findViewById(R.id.textViewNA);
        naValue.setText("NA: " + naSlider.getProgress()/100);

        naSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    int NA_global; //progress value of seekbar
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        NA_global = progress;
                        naValue.setText("NA: " + String.format("%.2f", ((maxNA-minNA) * NA_global /100) + minNA));
                        slideToNA(NA_global);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        naValue.setText("NA: " + String.format("%.2f", ((maxNA-minNA) * NA_global /100) + minNA));
                        slideToNA(NA_global);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        naValue.setText("NA: " + String.format("%.2f", ((maxNA-minNA) * NA_global /100) + minNA));
                        slideToNA(NA_global);
                    }
                }
        );


         /*
        4. Adjust XPos using slider (seekbar)
        Define Centre of the NA-Illumination
        */

        xposSlider = (SeekBar) findViewById(R.id.seekBarXPos);
        xposSlider.setMax(100);
        xposSlider.setProgress(initialXPosValue);

        xposValue = (TextView) findViewById(R.id.textViewXPos);
        xposValue.setText("X-Pos: " + xposSlider.getProgress()/100);

        xposSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    int XPos_global; //progress value of seekbar
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        XPos_global = progress - 50;
                        xposValue.setText("X-Pos: " + String.format("%.2f", XPos_global*1.));
                        slideToXPos(XPos_global);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        xposValue.setText("X-Pos: " + String.format("%.2f", XPos_global*1.));
                        slideToXPos(XPos_global);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        xposValue.setText("X-Pos: " + String.format("%.2f", XPos_global*1.));
                        slideToXPos(XPos_global);
                    }
                }
        );


          /*
        5. Adjust YPos using slider (seekbar)
        Define Centre of the NA-Illumination
        */

        yposSlider = (SeekBar) findViewById(R.id.seekBarYPos);
        yposSlider.setMax(100);
        yposSlider.setProgress(initialYPosValue);

        yposValue = (TextView) findViewById(R.id.textViewYPos);
        yposValue.setText("Y-Pos: " + yposSlider.getProgress());

        yposSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    int YPos_global; //progress value of seekbar
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        YPos_global = progress - 50;
                        yposValue.setText("Y-Pos: " + String.format("%.2f", YPos_global*1.));
                        slideToYPos(YPos_global);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        yposValue.setText("Y-Pos: " + String.format("%.2f", YPos_global*1.));
                        slideToYPos(YPos_global);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        yposValue.setText("Y-Pos: " + String.format("%.2f", YPos_global*1.));
                        slideToYPos(YPos_global);
                    }
                }
        );



        //Create second surface with another holder (holderTransparent) for drawing the rectangle
        SurfaceView transparentView = (SurfaceView) findViewById(R.id.TransparentView);
        transparentView.setBackgroundColor(Color.TRANSPARENT);
        transparentView.setZOrderOnTop(true);    // necessary
        SurfaceHolder holderTransparent = transparentView.getHolder();
        holderTransparent.setFormat(PixelFormat.TRANSPARENT);
        //TODO holderTransparent.addCallback(callBack);



        //Set Text and Button in UI
        btnCapture = (Button) findViewById(R.id.btnCapture);
        btnSetup = (Button) findViewById(R.id.btnSetup);
        btnProcess = (Button) findViewById(R.id.btnProcess);
        btnAcquire = (Button) findViewById(R.id.btnSaveFrame);
        btnAcquireBG = (Button) findViewById(R.id.btnBackground);
        btnShowResult = (Button) findViewById(R.id.btnResult);
        btnShowResult.setEnabled(false);
        btnAcquire.setText(acquireType);

        acquireTextView = (TextView) findViewById(R.id.acquireStatusTextView);
        acquireTextView2 = (TextView) findViewById(R.id.acquireStatusTextView2);
        timeLeftTextView = (TextView) findViewById(R.id.timeLeftTextView);
        acquireProgressBar = (ProgressBar) findViewById(R.id.acquireProgressBar);

        acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

        //Setup Colours
        acquireTextView.setTextColor(Color.YELLOW);
        acquireTextView2.setTextColor(Color.YELLOW);
        timeLeftTextView.setTextColor(Color.YELLOW);

        //getActionBar().setDisplayHomeAsUpEnabled(true);

        settingsDialogFragment = new AcquireSettings();




        btnAcquire.setOnClickListener(new View.OnClickListener()
        {public void onClick(View v) {
                                              Log.i(TAG, acquireType);
                                              if (acquireType.equals(BFMode)) {
                                                  new runBFMode().execute();
                                              } else if (acquireType.equals(MMMode)) {
                                                  new runMMMode().execute();
                                              } else if (acquireType.equals(FPMMode)) {
                                                  new runFPMMode().execute();
                                              } else if (acquireType.equals(DFMode)) { // TODO: Implement DF Mode
                                                  new runDFMode().execute();
                                              } else if (acquireType.equals(DPCMode)) {
                                                  new runDPCMode().execute();
                                              }
                                      }});


        btnSetup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSettingsDialog();
            }
        });

        btnCapture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new runCapture().execute();
            }
        });



        btnAcquireBG.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                acquireBackground = true;
                Log.i(TAG, acquireType);
                if (acquireType.equals(BFMode)) {
                    new runBFMode().execute();
                } else if (acquireType.equals(MMMode)) {
                    new runMMMode().execute();
                } else if (acquireType.equals(FPMMode)) {
                    new runFPMMode().execute();
                } else if (acquireType.equals(DFMode)) {
                    new runDFMode().execute();
                } else if (acquireType.equals(DPCMode)) {
                    new runDPCMode().execute();
                }
            }
        });


        btnShowResult.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent_showresult = new Intent();
                intent_showresult.putExtra("imagepath", g_DPC_path+"/result.tiff");
                intent_showresult.setClass(getApplicationContext(),AcquireResultActivity.class);
                startActivity(intent_showresult);
            }
        });






        btnProcess.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new runProcess().execute();
            }
        });


        // BEGIN_INCLUDE(getMediaRouter)
        // Get the MediaRouter service
        mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
        // END_INCLUDE(getMediaRouter)

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        //client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();


        int temp_center_index = (int)loadValue("Centerposition");
        Log.i(TAG, "Centerposition "+String.valueOf(temp_center_index));

        //TODO take care of these values!
        //global_cx = (int) Math.round(loadArray("maxValPosX").get(temp_center_index));
        //global_cy = (int) Math.round(loadArray("maxValPosY").get(temp_center_index));
        Log.i(TAG, "global_cx "+String.valueOf(global_cx));
        Log.i(TAG, "global_cy "+String.valueOf(global_cy));


        mBitmap = getCircle(global_cx, global_cy, global_NA_default);
        showNextColor();
    }

    double loadValue(String key)
    {
        // method to read an array-list from the preferences
        double value = 0;
        SharedPreferences mSharedPreference1 = PreferenceManager.getDefaultSharedPreferences(this);

        try{
            value = Double.parseDouble(mSharedPreference1.getString(key, null));
        }
        catch (Exception e){
            value = 0.0;
        }

        return value;
    }

    ArrayList<Double> loadArray(String key)
    {
        // method to read an array-list from the preferences
        ArrayList<Double> valList = new ArrayList<Double>();
        SharedPreferences mSharedPreference1 = PreferenceManager.getDefaultSharedPreferences(this);

        int size = mSharedPreference1.getInt("Status_size_"+key, 0);

        for(int i=0;i<size;i++)
        {
            valList.add(Double.parseDouble(mSharedPreference1.getString(key + i, null)));
        }

        return valList;
    }

    //**********************************************************************************************
    //  Method OnPause
    //**********************************************************************************************
    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");

        closeCamera();
        stopBackgroundThread();

        // BEGIN_INCLUDE(getMediaRouter)
        // Get the MediaRouter service
        mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);

    }

    //**********************************************************************************************
    //  Method OnREsume
    //**********************************************************************************************


    @Override
    public void onResume() {
        super.onResume();
        // BEGIN_INCLUDE(addCallback)
        // Register a callback for all events related to live video devices
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        // END_INCLUDE(addCallback)

        // Update the displays based on the currently active routes
        updatePresentation();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onDestroy() {
        super.onDestroy();
    }


    //**********************************************************************************************
    //  DialogStuff
    //**********************************************************************************************
    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
    }


    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();

        // BEGIN_INCLUDE(onStop)
        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        // BEGIN_INCLUDE(onStop)
    }


    //**********************************************************************************************
    //  Presentation Stuff CLASS
    //**********************************************************************************************
    private class runCapture extends AsyncTask<Void, Void, Void> {
        File myDir;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String l_capture_path = "/Beamerscope/Capture/"+ timestamp + "/";
            g_Capture_path = Environment.getExternalStorageDirectory()+l_capture_path;
            myDir = new File(g_Capture_path);

            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }


            // Try to make waiting a bit more fun...
            progressDialog = new ProgressDialog(AcquireActivity.this);
            progressDialog.setTitle("Please insert object..");
            progressDialog.setMessage("Waiting...");
            progressDialog.setCancelable(false);



            timeLeftTextView.setText("Capture with current light source!");

            // let the camera warm up
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        @Override
        protected void onProgressUpdate(Void... params) {
        }

        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            full_image_file = new File(myDir+"/Capture_Intensity.tiff");
            cameraReady = false;
            captureImage();
            while(!cameraReady)
            {
                mSleep(1);
            }
            imwriteNorm(mBitmap, myDir+"/Capture_IllSrc.tiff");
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            // free memory
            System.gc();

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);
        }

        public void captureImage() {
            takePicture();
        }
    }

    private class runDFMode extends AsyncTask<Void, Void, Void> {

        long t = 0;
        int i = 0;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
        String path = "/Beamerscope/dfmode_" + datasetName + "_" + timestamp + "/";
        File myDir = new File(Environment.getExternalStorageDirectory() + path);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());


            // if Background images have to be acquired it'll be saved for all acquisitions
            if(!acquireBackground){
                g_DF_path = Environment.getExternalStorageDirectory()+"/Beamerscope/DFMode/"+ timestamp + "/";
                myDir = new File(g_DF_path);
            }
            else{
                myDir = new File(g_DF_path_BG);
                acquireBackground = false;
            }



            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }


            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(5 * mmCount);


            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mDataset.DATASET_PATH = Environment.getExternalStorageDirectory() + path;
            //mDataset.DATASET_TYPE = acquireType;

        }

        @Override
        protected void onProgressUpdate(Void... params) {

            // make sure to close the current Presentation, otherwise all instances will eat up the
            // memory... TODO: make something like an update rather than opening/closing?
            //updatePresentation();
            showNextColor();

            acquireProgressBar.setProgress(i);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (mmCount * 5 - i) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, i, 5 * mmCount));
            Log.d(TAG, String.format("Time left: %.2f seconds", timeLeft));


        }


        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            Log.i("CAM2", "do in Background started");
            t = SystemClock.elapsedRealtime();

            publishProgress();

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();
            long startTime = SystemClock.elapsedRealtime();

            for (i = 0; i < mmCount; i++) // one count i per cycle
            {

                // Brightfield
                mBitmap = CreatePatterns.getDarkfield(global_cx, global_cy, global_na, global_na+30);

                publishProgress();
                mSleep(500); //Let AEC stabalize if it's on

                // initialize file names for this LED


                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
              /*
              dng_file = new File(Environment.getExternalStorageDirectory()+path, "pic" +
                                    timestamp + "_scanning_" + "dng_image" + ".dng");
                Log.i("PXINFO", "doInBackground: " + dng_file.getAbsolutePath());

                txt_file = new File(Environment.getExternalStorageDirectory()+path, "pic" +
                        timestamp + "_scanning_" + "dng_image" + ".txt");
                Log.i("PXINFO", "doInBackground: " + txt_file.getAbsolutePath());

                file = new File(Environment.getExternalStorageDirectory()+path, "scanning_" + String.format("%04d", index) + ".tiff");
                Log.i("PXINFO", "doInBackground: " + file.getAbsolutePath());

                blue_cropped_file =  new File(Environment.getExternalStorageDirectory()+path, "scanning_blue_cropped_" +
                        String.format("%04d", index) +  ".tiff");
                Log.i("PXINFO", "doInBackground: " + blue_cropped_file.getAbsolutePath());

                cropped_image_file = new File(Environment.getExternalStorageDirectory()+path, "pic" +
                        timestamp + "_full_cropped_" + String.format("%04d", index) +  ".tiff");
                Log.i("PXINFO", "doInBackground: " + cropped_image_file.getAbsolutePath());
                */


                // initialize file names for this method
                full_image_file = new File(myDir+"/DF" + "_" + String.format("%04d", i) + ".tiff");
                Log.i("PXINFO", "doInBackground: " + full_image_file.getAbsolutePath());


                cameraReady = false;
                captureImage();
                while(!cameraReady)
                {
                    mSleep(1);
                }


                // Save image from light-source
                Mat tmp = new Mat ();
                Utils.bitmapToMat(mBitmap, tmp);
                normalize(tmp, tmp, 0, 255, NORM_MINMAX);
                imwrite(g_DF_path+"illopt_result.png", tmp);




            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");

            updateFileStructure(myDir.getAbsolutePath());
            mDataset.DATASET_PATH = Environment.getExternalStorageDirectory() + path;
            //            mDataset.DATASET_TYPE = acquireType;

            //TODO            mDngCreator.close();

            // free memory
            System.gc();

            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);

            mBitmap = getCircle(global_cx, global_cy, global_NA_default);
            showNextColor();
        }


        public void captureImage() {
            takePicture();
        }
    }

    private class runMMMode extends AsyncTask<Void, Void, Void> {

        long t = 0;
        int i = 0;
        File myDir;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());


            // if Background images have to be acquired it'll be saved for all acquisitions
            if(!acquireBackground){
                g_BF_path = Environment.getExternalStorageDirectory()+"/Beamerscope/MMMode/"+ timestamp + "/";
                myDir = new File(g_MM_path);
            }
            else{
                myDir = new File(g_MM_path_BG);
                acquireBackground = false;
            }



            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }


            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(5 * mmCount);



        }

        @Override
        protected void onProgressUpdate(Void... params) {

            // make sure to close the current Presentation, otherwise all instances will eat up the
            // memory... TODO: make something like an update rather than opening/closing?
            //updatePresentation();
            showNextColor();

            acquireProgressBar.setProgress(i);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (mmCount * 5 - i) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, i, 5 * mmCount));
            Log.d(TAG, String.format("Time left: %.2f seconds", timeLeft));


        }


        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            Log.i("CAM2", "do in Background started");
            t = SystemClock.elapsedRealtime();

            publishProgress();

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();
            long startTime = SystemClock.elapsedRealtime();

            for (i = 0; i < mmCount; i++) // one count i per cycle
            {

                // Brightfield
                mBitmap = CreatePatterns.getSegments(generateRanNumVect(48), global_cx, global_cy, global_na);

                publishProgress();
                mSleep(500); //Let AEC stabalize if it's on

                // initialize file names for this method
                full_image_file = new File(myDir+"/MM" + "_" + String.format("%04d", i) + ".tiff");
                Log.i("PXINFO", "doInBackground: " + full_image_file.getAbsolutePath());

                cameraReady = false;
                captureImage();
                while(!cameraReady)
                {
                    mSleep(1);
                }



                // Save image from light-source
                Mat tmp = new Mat ();
                Utils.bitmapToMat(mBitmap, tmp);
                normalize(tmp, tmp, 0, 255, NORM_MINMAX);
                imwrite(g_MM_path+"/MM_illsrc_" + String.format("%04d", i) + ".png", tmp);



            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");

            updateFileStructure(myDir.getAbsolutePath());

            // free memory
            System.gc();

            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);

            mBitmap = getCircle(global_cx, global_cy, global_NA_default);
            showNextColor();
        }


        public void captureImage() {
            takePicture();
        }
    }

    private class runBFMode extends AsyncTask<Void, Void, Void> {

        long t = 0;
        int i = 0;
        File myDir;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());


            // if Background images have to be acquired it'll be saved for all acquisitions
            if(!acquireBackground){
                g_BF_path = Environment.getExternalStorageDirectory()+"/Beamerscope/BFMode/"+ timestamp + "/";
                myDir = new File(g_BF_path);
            }
            else{
                myDir = new File(g_BF_path_BG);
                acquireBackground = false;
            }



            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }


            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(5 * mmCount);



        }

        @Override
        protected void onProgressUpdate(Void... params) {

            // make sure to close the current Presentation, otherwise all instances will eat up the
            // memory... TODO: make something like an update rather than opening/closing?
            //updatePresentation();
            showNextColor();

            acquireProgressBar.setProgress(i);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (mmCount * 5 - i) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, i, 5 * mmCount));
            Log.d(TAG, String.format("Time left: %.2f seconds", timeLeft));


        }


        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            Log.i("CAM2", "do in Background started");
            t = SystemClock.elapsedRealtime();

            publishProgress();

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();
            long startTime = SystemClock.elapsedRealtime();

            for (i = 0; i < mmCount; i++) // one count i per cycle
            {

                // Brightfield
                mBitmap = CreatePatterns.getCircle(global_cx, global_cy, (int)(i*global_na/mmCount));

                publishProgress();
                mSleep(200);
                // Save images illuminated with optimized illumination
                full_image_file = new File(myDir+"/BF" + "_" + String.format("%04d", i) + ".tiff");
                Log.i("PXINFO", "doInBackground: " + full_image_file.getAbsolutePath());

                cameraReady = false;
                captureImage();
                while(!cameraReady)
                {
                    mSleep(1);
                }




                // Save image from light-source
                Mat tmp = new Mat ();
                Utils.bitmapToMat(mBitmap, tmp);
                normalize(tmp, tmp, 0, 255, NORM_MINMAX);
                imwrite(g_MM_path+"/BF_illsrc_" + String.format("%04d", i) + ".png", tmp);


                mSleep(600);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");

            updateFileStructure(myDir.getAbsolutePath());

            // free memory
            System.gc();

            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);

            mBitmap = getCircle(global_cx, global_cy, global_NA_default);
            showNextColor();
        }


        public void captureImage() {
            takePicture();
        }
    }

    private class runProcess extends AsyncTask<Void, Void, Void> {


        // Hardcoded Filenames from DPC // TODO: No Hardcoding!

        /*
        String dpc_fName_11 = "/storage/emulated/0/Beamerscope/dt.jpeg";//argv[1];
        String dpc_fName_12 = "/storage/emulated/0/Beamerscope/db.jpeg";//argv[1];
        String dpc_fName_21 = "/storage/emulated/0/Beamerscope/dl.jpeg";//argv[3];
        String dpc_fName_22 =  "/storage/emulated/0/Beamerscope/dr.jpeg";//argv[4];
         */



        String trans_fName_1 = "/storage/emulated/0/Beamerscope/dpcTransferFunc_45.tiff";//argv[5];
        String trans_fName_2 = "/storage/emulated/0/Beamerscope/dpcTransferFunc_135.tiff";//argv[6];

        // Intensity Images of the Object
        String dpc_fName_11 = g_DPC_path+"DPC_0000.tiff";
        String dpc_fName_12 = g_DPC_path+"DPC_0001.tiff";
        String dpc_fName_21 = g_DPC_path+"DPC_0002.tiff";
        String dpc_fName_22 = g_DPC_path+"DPC_0003.tiff";


        // Intensity Images of the Background
        String dpc_fName_11_BG = g_DPC_path_BG+"DPC_0000.tiff";
        String dpc_fName_12_BG = g_DPC_path_BG+"DPC_0001.tiff";
        String dpc_fName_21_BG = g_DPC_path_BG+"DPC_0002.tiff";
        String dpc_fName_22_BG = g_DPC_path_BG+"DPC_0003.tiff";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(TAG, "dpc_fName_11 "+ dpc_fName_11);
            Log.i(TAG, "dpc_fName_11_BG "+ dpc_fName_11_BG);
            Log.i(TAG, "g_DPC_path "+ g_DPC_path);
            Log.i(TAG, "g_DPC_path_BG "+ g_DPC_path_BG);


            btnAcquire.setEnabled(false);
            btnProcess.setEnabled(false);
            btnSetup.setEnabled(false);

            inAnimation = new AlphaAnimation(0f, 1f);
            inAnimation.setDuration(200);
            progressBarHolder.setAnimation(inAnimation);
            progressBarHolder.setVisibility(View.VISIBLE);

        }

        @Override
        protected void onProgressUpdate(Void... params) {
        }

        @Override
        protected Void doInBackground(Void... params) {


            // Read Transferfunction precumpeted in MATLAB
            Mat trans1 = imread(trans_fName_1, CV_LOAD_IMAGE_ANYDEPTH);
            Mat trans2 = imread(trans_fName_2, CV_LOAD_IMAGE_ANYDEPTH);

            //check if all files exist:
            //read in files. -1 for all permissions
            Mat dpc11 = imread(dpc_fName_11);
            Mat dpc12 = imread(dpc_fName_12);
            Mat dpc21 = imread(dpc_fName_21);
            Mat dpc22 = imread(dpc_fName_22);

            // read in background images
            Mat dpc11_bg = imread(dpc_fName_11_BG);
            Mat dpc12_bg = imread(dpc_fName_12_BG);
            Mat dpc21_bg = imread(dpc_fName_21_BG);
            Mat dpc22_bg = imread(dpc_fName_22_BG);

            // TODO: handle case if background is empty!!
            if((dpc11.width()==0)) {
                Log.e(TAG, "*********1**********");
            }
            if((dpc12.width()==0)) {
                Log.e(TAG, "********2***********");
            }
            if((dpc21.width()==0)) {
                Log.e(TAG, "********3***********");
            }
            if((dpc22.width()==0)) {
                Log.e(TAG, "*********4**********");
            }
            if((trans1.width()==0)) {
                Log.e(TAG, "********5***********");
            }
            if((trans2.width()==0)) {
                Log.e(TAG, "**********6*********");
            }
            if((dpc11_bg.width()==0)) {
                dpc11_bg = Mat.ones(dpc11.size(), dpc11.type());
                Log.e(TAG, "*********1_bg**********");
            }
            if((dpc12_bg.width()==0)) {
                dpc12_bg =  Mat.ones(dpc11.size(), dpc11.type());
                Log.e(TAG, "********2_bg***********");
            }
            if((dpc21_bg.width()==0)) {
                dpc21_bg = Mat.ones(dpc11.size(), dpc11.type());
                Log.e(TAG, "********3_bg***********");
            }
            if((dpc22_bg.width()==0)) {
                dpc22_bg = Mat.ones(dpc11.size(), dpc11.type());
                Log.e(TAG, "*********4_bg**********");
            }

            // subtract background
            divide(dpc11, dpc11_bg, dpc11);
            divide(dpc12, dpc12_bg, dpc12);
            divide(dpc21, dpc21_bg, dpc21);
            divide(dpc22, dpc22_bg, dpc22);

            // save images for debug
            imwriteNorm(dpc11, g_DPC_path+"dpc11.tiff");
            imwriteNorm(dpc12, g_DPC_path+"dpc12.tiff");
            imwriteNorm(dpc21, g_DPC_path+"dpc21.tiff");
            imwriteNorm(dpc22, g_DPC_path+"dpc22.tiff");



            // Handle Sizes of image
            int dpcSubsize = 400;




            // resize
            // get center-cropped part of the qDPC image
            // submat and extract green channel of image 1

            Mat dpc11_sub = ImageUtils.ExtractAndCropMat(dpc11, dpcSubsize, 1);
            Mat dpc12_sub = ImageUtils.ExtractAndCropMat(dpc12, dpcSubsize, 1);
            Mat dpc21_sub = ImageUtils.ExtractAndCropMat(dpc21, dpcSubsize, 1);
            Mat dpc22_sub = ImageUtils.ExtractAndCropMat(dpc22, dpcSubsize, 1);



            // Calculate the qDPC Image from the input images
            Mat MatQDPC = new Mat();
            Mat MatDiffX = new Mat();
            Mat MatDiffY = new Mat();
            qDPC(dpc11_sub.getNativeObjAddr(), dpc12_sub.getNativeObjAddr(), dpc21_sub.getNativeObjAddr(), dpc22_sub.getNativeObjAddr(), trans1.getNativeObjAddr(), trans2.getNativeObjAddr(), MatQDPC.getNativeObjAddr(), MatDiffX.getNativeObjAddr(), MatDiffY.getNativeObjAddr());
            Log.e(TAG, "*********WriteIm**********");
            // save image
            imwriteNorm(MatQDPC, g_DPC_path+"result.tiff");
            imwriteNorm(MatDiffX, g_DPC_path+"result_diffX.tiff");
            imwriteNorm(MatDiffY, g_DPC_path+"result_diffY.tiff");

            // Handle Sizes of image
            int qdpcWidth = MatQDPC.width();
            int qdpcHeight = MatQDPC.height();
            int qdpcSubsize = (int) INPUT_SIZE[2]; // 128

            // get center-cropped part of the qDPC image
            MatQDPC = MatQDPC.submat((qdpcHeight-qdpcSubsize)/2, (qdpcHeight+qdpcSubsize)/2,
                    (qdpcWidth-qdpcSubsize)/2, (qdpcWidth+qdpcSubsize)/2);
            Mat MatQDPCsub = MatQDPC.clone();  // make sure that mat is continuous
            Log.i(TAG, "MatQDPCsub: "+(String.valueOf(MatQDPCsub)));

            // transform Euler to Real/Imag writing
            Mat qDPC_real = new Mat();
            Mat qDPC_imag = new Mat();
            Core.polarToCart(Mat.ones((int) INPUT_SIZE[1], (int) INPUT_SIZE[2], MatQDPCsub.type()), MatQDPCsub, qDPC_real, qDPC_imag);

            // Mat input2D
            List<Mat> MatList = new ArrayList<>();
            //TODO: assign Real/Imag Part
            MatList.add(qDPC_real);    // first layer is real part
            MatList.add(qDPC_imag);    // first layer is imaginary part

            Mat qDPCMat = new Mat();
            merge(MatList, qDPCMat); qDPCMat.convertTo(qDPCMat, CvType.CV_32F);
            Log.i(TAG, "qDPCMat: "+String.valueOf(qDPCMat));

            // Create fake information - usually it's a 3D image, where 1st-layer = real and 2nd = imag
            /*
            double mean = 0.0;
            double stddev = 500.0 / 3.0; // 99.7% of values will be inside [-500, +500] interval
            randn(qDPCMat, mean, stddev);
            */

            // Need to convert TestMat to float array to feed into TF
            MatOfFloat TF_input_f = new MatOfFloat(CvType.CV_32F);
            qDPCMat.convertTo(TF_input_f,CvType.CV_32F);
            float[] TF_input = new float[(int)(TF_input_f.total()*TF_input_f.channels())];
            TF_input_f.get(0, 0, TF_input);
            Log.i("floatArray", String.valueOf(TF_input.length));

            // feed the data to the input node
            Log.i("INPUT_NODE", String.valueOf(INPUT_NODE));
            Log.i("TF_input", String.valueOf(TF_input));
            Log.i("INPUT_SIZE#", String.valueOf(INPUT_SIZE));

            inferenceInterface.feed(INPUT_NODE, TF_input, INPUT_SIZE);

            // define ouput Data to store result
            TF_result_after_NN = new float[(int) (OUTPUT_SIZE)];

            // run inference model in native code
            inferenceInterface.run(new String[] {OUTPUT_NODE});
            inferenceInterface.fetch(OUTPUT_NODE,TF_result_after_NN);
            //Downstairs   Jogging      Sitting  Standing   Upstairs   Walking


            // TODO Take care that these datas are converted correctly!
            // convert array to Mat object
            Mat matResult = new Mat(1, OUTPUT_SIZE, CvType.CV_32F); // first is ROW, then COL

            for(int col=0;col<OUTPUT_SIZE;col++) {
                matResult.put(1, col, TF_result_after_NN[col]);
                Log.i("Result of TF output", String.valueOf(TF_result_after_NN[col]));

            }

            // TODO: No need to normalize - NN should give right results anyway!
            normalize(matResult, matResult, 0, 1, NORM_MINMAX);

            mBitmap = CreatePatterns.getSegments(TF_result_after_NN, global_cx, global_cy, global_na/2);

            // Save image from light-source
            imwriteNorm(mBitmap, g_DPC_path+"illopt_result.png");

            return null;


        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            outAnimation = new AlphaAnimation(1f, 0f);
            outAnimation.setDuration(200);
            progressBarHolder.setAnimation(outAnimation);
            progressBarHolder.setVisibility(View.GONE);
            btnSetup.setEnabled(true);
            btnProcess.setEnabled(true);
            btnAcquire.setEnabled(true);

            showNextColor();
            btnShowResult.setEnabled(true);



            publishProgress();
            mSleep(200);
            // Save images illuminated with optimized illumination
            full_image_file = new File(g_DPC_path + "qDPC_illopt_result.tiff");

            cameraReady = false;
            captureImage();
            while(!cameraReady)
            {
                mSleep(1);
            }



            // check that, the optimized illuminaiton source has been calculated
            g_illumination_processed = true;

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);


        }

        public void captureImage() {
            takePicture();
        }

        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private class runFPMMode extends AsyncTask<Void, Void, Void> {

        int n = 0;
        long t = 0;

        File myDir;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());


            // if Background images have to be acquired it'll be saved for all acquisitions
            if(!acquireBackground){
                g_FPM_path = Environment.getExternalStorageDirectory()+"/Beamerscope/FPMMode/"+ timestamp + "/";
                myDir = new File(g_FPM_path);
            }
            else{
                myDir = new File(g_FPM_path_BG);
                acquireBackground = false;
            }



            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }



            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(mmCount * mmCount);


            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        @Override
        protected void onProgressUpdate(Void... params) {
            acquireProgressBar.setProgress(n);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (mmCount * mmCount - n) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, n, 5 * mmCount));
            Log.d(TAG, String.format("Time left: %.2f seconds", timeLeft));


            showNextColor();
        }


        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            t = SystemClock.elapsedRealtime();

            int NA_fpm = 5;        // radius of illumination sub-aperture
            int dx = 25;                // spacing between sub-apertures in x-directions
            int dy = 25;                // spacing between sub-apertures in y-directions

            publishProgress();

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();
            long startTime = SystemClock.elapsedRealtime();

            for (int nx = 0; nx < mmCount; nx++) {
                for (int ny = 0; ny < mmCount; ny++) {

                    // Acquire FPM Images


                    int cx = (int)(nx-mmCount/2)*dx+global_cx;
                    int cy = (int)(ny-mmCount/2)*dy+global_cy;

                    Log.e(TAG, String.valueOf(cx)+ ", "+ String.valueOf(cy));
                    mBitmap = CreatePatterns.getCircle(cx, cy, NA_fpm);

                    publishProgress();
                    mSleep(200);
                    // initialize file names for this LED
                    full_image_file = new File(myDir+"/DPC" + "_" + String.format("%04d", nx) +"_" + String.format("%04d", ny) + ".tiff");

                    cameraReady = false;
                    captureImage();
                    while(!cameraReady)
                    {
                        mSleep(1);
                    }

                    n++;

                }
            }
        return null;
        }


        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");

            updateFileStructure(myDir.getAbsolutePath());
            mDataset.DATASET_PATH = Environment.getExternalStorageDirectory() + g_FPM_path;

            //            mDataset.DATASET_TYPE = acquireType;

            //TODO            mDngCreator.close();

            // free memory
            System.gc();

            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            mBitmap = getCircle(global_cx, global_cy, global_NA_default);
            showNextColor();


        }


        public void captureImage() {
            takePicture();
        }

    }

    private class runDPCMode extends AsyncTask<Void, Void, Void> {
        File myDir;

        int n = 0;
        long t = 0;

        // iterator for all rotational angles
        int i = 0;

        // Parameters for illumination source
        // Order: top, bottom , left, right
        int[] rotation_Angle = {0, 180, 90, 270};   // Orientation/Angles of half-circle


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());


            // if Background images have to be acquired it'll be saved for all acquisitions
            if(!acquireBackground){
                g_DPC_path = Environment.getExternalStorageDirectory()+"/Beamerscope/DPCMode/"+ timestamp + "/";
                myDir = new File(g_DPC_path);
            }
            else{
                g_DPC_path_BG = Environment.getExternalStorageDirectory()+"/Beamerscope/DPCMode_Background/"+ timestamp + "/";
                myDir = new File(g_DPC_path_BG);
                acquireBackground = false;
            }



            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }


            // Try to make waiting a bit more fun...
            progressDialog = new ProgressDialog(AcquireActivity.this);
            progressDialog.setTitle("Please insert object..");
            progressDialog.setMessage("Waiting...");
            progressDialog.setCancelable(false);



            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(mmCount * mmCount);

            mSleep(100);
        }

        @Override
        protected void onProgressUpdate(Void... params) {

            // update Presentation
            showNextColor();

            acquireProgressBar.setProgress(n);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (mmCount * mmCount - n) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, n, 5 * mmCount));

            if(i > rotation_Angle.length-1){
                //progressDialog.show();
            }
            else{
                progressDialog.dismiss();
            }
        }

        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();

                for (i = 0; i < rotation_Angle.length; i++) {
                    // Acquire DPC Images


                    // Generate DPC Illumination Shape
                    mBitmap = CreatePatterns.getDPC(global_cx, global_cy, rotation_Angle[i], 200);
                    publishProgress();
                    mSleep(200);


                    full_image_file = new File(myDir+"/DPC" + "_" + String.format("%04d", i) + ".tiff");
                    Log.i("PXINFO", "doInBackground: " + full_image_file.getAbsolutePath());
                    cameraReady = false;
                    captureImage();
                    while(!cameraReady)
                    {
                        mSleep(1);
                    }


                }


            // Generate BF Illumination Shape
            mBitmap = CreatePatterns.getCircle(global_cx, global_cy, global_na);
            publishProgress();
            mSleep(200);


            full_image_file = new File(myDir+"/BF_comparison.tiff");
            Log.i("PXINFO", "doInBackground: " + full_image_file.getAbsolutePath());
            cameraReady = false;
            captureImage();
            while(!cameraReady)
            {
                mSleep(1);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up


            // delete the pgrogress dialog
            progressDialog.dismiss();
            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");
            mDataset.DATASET_PATH = Environment.getExternalStorageDirectory() + g_DPC_path;
            //            mDataset.DATASET_TYPE = acquireType;

            //TODO            mDngCreator.close();

            // free memory
            System.gc();

            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            // Reavtivate Exposure Time
            slideToExposuretime(progressValueExposure);

            showNextColor();

        }

        public void captureImage() {
            takePicture();
        }
    }


    // Differentiate between different Illumination Pattern Sources
    // This can be either a BMP, from File or from Asset

    public void updateFileStructure(String currPath) {
        File f = new File(currPath);
        File[] fileList = f.listFiles();
        ArrayList<String> arrayFiles = new ArrayList<String>();
        if (!(fileList.length == 0)) {
            for (int i = 0; i < fileList.length; i++)
                arrayFiles.add(currPath + "/" + fileList[i].getName());
        }

        String[] fileListString = new String[arrayFiles.size()];
        fileListString = arrayFiles.toArray(fileListString);
        MediaScannerConnection.scanFile(AcquireActivity.this,
                fileListString, null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                    }
                });
    }

    public void openSettingsDialog() {
        settingsDialogFragment.show(getFragmentManager(), "acquireSettings2");
    }

    public void setHDR(boolean state) {
        if (state)
            usingHDR = true;
        else
            usingHDR = false;
    }

    public void setDatasetName(String name) {
        datasetName = name;
    }

    public void setISO(String iso) {
        isoSetting = Integer.parseInt(iso);
        Log.i("ISO return", Integer.toString(isoSetting));

        //set camera iso value
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSetting);

        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }

    //------------------------CAMERA 2 API STUFF----------------------------------------------------
    //Conversion from screen rotation to JPEG orientation.

    /**
     * Implementing a {@link android.media.MediaRouter.Callback} to update the displayed
     * {@link android.app.Presentation} when a route is selected, unselected or the
     * presentation display has changed. The provided stub implementation
     * {@link android.media.MediaRouter.SimpleCallback} is extended and only
     * {@link android.media.MediaRouter.SimpleCallback#onRouteSelected(android.media.MediaRouter, int, android.media.MediaRouter.RouteInfo)}
     * ,
     * {@link android.media.MediaRouter.SimpleCallback#onRouteUnselected(android.media.MediaRouter, int, android.media.MediaRouter.RouteInfo)}
     * and
     * {@link android.media.MediaRouter.SimpleCallback#onRoutePresentationDisplayChanged(android.media.MediaRouter, android.media.MediaRouter.RouteInfo)}
     * are overridden to update the displayed {@link android.app.Presentation} in
     * {@link #updatePresentation()}. These callbacks enable or disable the
     * second screen presentation based on the routing provided by the
     * {@link android.media.MediaRouter} for {@link android.media.MediaRouter#ROUTE_TYPE_LIVE_VIDEO}
     * streams. @
     */


    /**
     * Listens for dismissal of the {@link MyPresentation} and removes its
     * reference.
     */
    public final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog == mPresentation) {
                        mPresentation = null;
                    }
                }
            };


    public final MediaRouter.SimpleCallback mMediaRouterCallback =
            new MediaRouter.SimpleCallback() {

                // BEGIN_INCLUDE(SimpleCallback)
                /**
                 * A new route has been selected as active. Disable the current
                 * route and enable the new one.
                 */
                @Override
                public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
                    updatePresentation();
                }

                /**
                 * The route has been unselected.
                 */
                @Override
                public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
                    updatePresentation();

                }

                /**
                 * The route's presentation display has changed. This callback
                 * is called when the presentation has been activated, removed
                 * or its properties have changed.
                 */
                @Override
                public void onRoutePresentationDisplayChanged(MediaRouter router, MediaRouter.RouteInfo info) {
                    updatePresentation();
                }
                // END_INCLUDE(SimpleCallback)
            };

    /**
     * Updates the displayed presentation to enable a secondary screen if it has
     * been selected in the {@link android.media.MediaRouter} for the
     * {@link android.media.MediaRouter#ROUTE_TYPE_LIVE_VIDEO} type. If no screen has been
     * selected by the {@link android.media.MediaRouter}, the current screen is disabled.
     * Otherwise a new {@link MyPresentation} is initialized and shown on
     * the secondary screen.
     */
    public void updatePresentation() {



        // BEGIN_INCLUDE(updatePresentationInit)
        // Get the selected route for live video
        MediaRouter.RouteInfo selectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        // Get its Display if a valid route has been selected
        Display selectedDisplay = null;
        if (selectedRoute != null) {
            selectedDisplay = selectedRoute.getPresentationDisplay();
        }
        // END_INCLUDE(updatePresentationInit)

        // BEGIN_INCLUDE(updatePresentationDismiss)
        /*
         * Dismiss the current presentation if the display has changed or no new
         * route has been selected
         */
        try{
            Log.i(TAG, "ZZ1 mPresentation != null "+String.valueOf(mPresentation != null));
            Log.i(TAG, "ZZ2 mPresentation.getDisplay() != selectedDisplay "+String.valueOf(mPresentation.getDisplay() != selectedDisplay));

        }
        catch (Exception e){}

        if (mPresentation != null && mPresentation.getDisplay() != selectedDisplay) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        // END_INCLUDE(updatePresentationDismiss)

        // BEGIN_INCLUDE(updatePresentationNew)
        /*
         * Show a new presentation if the previous one has been dismissed and a
         * route has been selected.
         */
        Log.i(TAG, "Start Bitmap set to Presentation Display");

        Log.i(TAG, "ZZ3 mPresentation == null "+String.valueOf(mPresentation == null));
        Log.i(TAG, "ZZ4 selectedDisplay != null "+String.valueOf(selectedDisplay != null));


        if (mPresentation == null && selectedDisplay != null) {

            // Initialise a new Presentation for the Display
            mPresentation = new MyPresentation(this, selectedDisplay);
            mPresentation.setOnDismissListener(mOnDismissListener);

            // Try to show the presentation, this might fail if the display has
            // gone away in the mean time
            try {
                Log.i(TAG, "Bitmap set to Presentation Display");
                mPresentation.show();
                mPresentation.setBitmap(mBitmap);
            } catch (WindowManager.InvalidDisplayException ex) {
                // Couldn't show presentation - display was already removed
                Log.e(TAG, "Error Bitmap set to Presentation Display");
                mPresentation = null;
            }
        }
        // END_INCLUDE(updatePresentationNew)

    }



    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = "Camera2BasicFragment";

    // Camera state: Showing camera preview.
    private static final int STATE_PREVIEW = 0;

    // * Camera state: Waiting for the focus to be locked.
    private static final int STATE_WAITING_LOCK = 1;

    // Camera state: Waiting for the exposure to be precapture state.
    private static final int STATE_WAITING_PRECAPTURE = 2;

    // Camera state: Waiting for the exposure state to be something other than precapture.
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    // Camera state: Picture was taken.
    private static final int STATE_PICTURE_TAKEN = 4;

    // Max preview width that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_WIDTH = 1920;

    //* Max preview height that is guaranteed by Camera2 API
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    //TextureView.SurfaceTextureListener handles several lifecycle events on a TextureView.
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    //ID of the current CameraDevice.
    private String mCameraId;

    //AutoFitTextureView for camera preview.
    AutoFitTextureView mTextureView;

    //A CameraCaptureSession for camera preview.
    private CameraCaptureSession mCaptureSession;

    //A reference to the opened CameraDevice
    private CameraDevice mCameraDevice;

    //The android.util.Size of camera preview.
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;


        }

    };

    //------------------------------------------------------------------------------------------------
    //
    /* Add ExposureTime functionality (26/9/2016)
    1. Varry Exposure Time using slider
     */

    //Get maximum exposuretime and sensor info of phone camera

    int upperExposuretime, lowerExposuretime;
    //long exposureTime;

    public void getExposuretimeCapability() {
        try {

            Object[] possibleValuesExposuretime = new Object[100];

            //Activity activity = getActivity();
            CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);


            Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);

            lowerExposuretime = (int) (exposureRange.getLower() / (long) 1000000) + 1;
            upperExposuretime = (int) ((exposureRange.getUpper()/100) / (long) 1000000); // upper Limit is far to high


            int range = upperExposuretime - lowerExposuretime;

            int incrementer = (int) (range / 100.0);


            for (int i = 0; i < 100; i++) {
                Integer value = i * incrementer + lowerExposuretime;
                possibleValuesExposuretime[i] = new Pair<Integer, String>(value, value.toString() + "ms");
            }


        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }
    }


    //1. Expsouretime the preview according to the progress of the Exposure slider
    public void slideToExposuretime(int exposuretime_val) {
        /*
          exposuretime_val is the exposure-time percentage displayed on the textView (a value between 0 and 100)
          exposuretime_val = 0 implies exposure_level = 1 ms
          exposuretime_val = 100 implies exposure_level = maxexposuretime
          Solve the straight line equation
        */
        getExposuretimeCapability();
        global_exposuretime  = (int) ((((upperExposuretime - lowerExposuretime) * exposuretime_val) / 99) + 1);

        Log.i(TAG, "Exposuretime: " + String.valueOf(global_exposuretime ));

        //set camera exposure time (default is 1000000 ns)
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) global_exposuretime  * 1000000);

        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

    }


    //------------------------------------------------------------------------------------------------
    //
    /* Add Zoom functionality (31/7/2016)
    1. Zooming using slider
    2. Pinch to zoom
     */

    //Get maximum zoom and sensor info of phone camera
    float maxzoom;

    Rect m;

    public void getZoomCapability() {
        try {
            //Activity activity = getActivity();
            CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            maxzoom = (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)) * 10;
            m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Log.d("getZoomCapability: ", String.valueOf(maxzoom));
        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }
    }

    /*
    Given the zoom_level, zoom the preview
     Ref: http://stackoverflow.com/questions/32711975/zoom-camera2-preview-using-textureview
          http://stackoverflow.com/questions/35968315/android-camera2-handle-zoom
     */

    public int zoom_level = 1;
    Rect zoom;
    boolean isZoomed = false;


    //1. Zoom the preview according to the progress of the zoom slider

    public void slideToZoom(int zoom_val) {
        /*
          zoom_val is the zoom percentage displayed on the textView (a value between 0 and 100)
          zoom_val = 0 implies zoom_level = 1
          zoom_val = 100 implies zoom_level = maxzoom
          Solve the straight line equation
        */
        getZoomCapability();
        zoom_level = (int) ((((maxzoom - 1) * zoom_val) / 99) + 1);

        // --- ZOOM HERE!

        isZoomed = true;
        int minW = (int) (m.width() / maxzoom);
        int minH = (int) (m.height() / maxzoom);
        int difW = m.width() - minW;
        int difH = m.height() - minH;
        int cropW = difW / 100 * (int) zoom_level;
        int cropH = difH / 100 * (int) zoom_level;
        cropW -= cropW & 3;
        cropH -= cropH & 3;

        zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);

        mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }


    //------------------------------------------------------------------------------------------------
    //
    /* Add change-NA functionality (31/7/2016)
    1. Change Illuminatino NA using slider
     */
    public void slideToNA(int na_val) {
        /*
          zoom_val is the zoom percentage displayed on the textView (a value between 0 and 100)
          zoom_val = 0 implies zoom_level = 1
          zoom_val = 100 implies zoom_level = maxzoom
          Solve the straight line equation
        */
        //Calculate NA-value from slider-position
        //na_level = (((maxNA-minNA) * na_val/100)+ minNA);

        global_na = na_val*2;
        if(g_illumination_processed & acquireType==DPCMode) mBitmap = getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        else if(acquireType==MMMode){
            TF_result_after_NN = generateRanNumVect(48);
            mBitmap = CreatePatterns.getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        }
        else if(acquireType==DPCMode) mBitmap = getDPC(global_cx, global_cy, 0, global_na);
        else if(acquireType==BFMode) mBitmap = getCircle(global_cx, global_cy, global_na);
        else if(acquireType==DFMode) mBitmap = getDarkfield(global_cx, global_cy, global_na, global_na+30);

        showNextColor();

    }


    //------------------------------------------------------------------------------------------------
    //
    /* Add change-X-centre position of NA
    1. Change Illuminatino NA using slider
     */
    public void slideToXPos(int x_val) {

        global_cx = x_val*5;

        if(g_illumination_processed & acquireType==DPCMode) mBitmap = getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        else if(acquireType==MMMode){
            TF_result_after_NN = generateRanNumVect(48);
            mBitmap = CreatePatterns.getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        }
        else if(acquireType==DPCMode) mBitmap = getDPC(global_cx, global_cy, 0, global_na);
        else if(acquireType==BFMode) mBitmap = getCircle(global_cx, global_cy, global_na);
        else if(acquireType==DFMode) mBitmap = getDarkfield(global_cx, global_cy, global_na, global_na+30);


        showNextColor();

    }


    //------------------------------------------------------------------------------------------------
    //
    /* Add change-X-centre position of NA
    1. Change Illuminatino NA using slider
     */
    public void slideToYPos(int y_val) {

        global_cy = y_val*5;


        if(g_illumination_processed & acquireType==DPCMode) mBitmap = getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        else if(acquireType==MMMode){
            TF_result_after_NN = generateRanNumVect(48);
            mBitmap = CreatePatterns.getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        }
        else if(acquireType==DPCMode) mBitmap = getDPC(global_cx, global_cy, 0, global_na);
        else if(acquireType==BFMode) mBitmap = getCircle(global_cx, global_cy, global_na);
        else if(acquireType==DFMode) mBitmap = getDarkfield(global_cx, global_cy, global_na, global_na+30);

        showNextColor();

    }

    /***
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /***
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /***
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            // Safe one Image
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), full_image_file));
        }
    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        // Change to CONTROL_AF_STATE
                        Integer aeState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    // Change to CONTROL_AF_STATE
                    Integer aeState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    // Change to CONTROL_AF_STATE
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        //final Activity activity = getActivity();
        if (this != null) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CaptureImageFragment.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CaptureImageFragment.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        //Activity activity = getActivity();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CaptureImageFragment.CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = this.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                this.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                //Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                //mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            //TODO CaptureImageFragment.ErrorDialog.newInstance(getString(R.string.camera_error)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified
     */
    private void openCamera(int width, int height) {

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            //TODO requestForPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        //Activity activity = getActivity();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);


            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {


                                //SETUP Everything in advance
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_MODE_OFF);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);

                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        cameraReady = false;
        try {
            //Lock the focus as the first step for a still image capture.
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #takePicture()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #takePicture()}.
     */
    private void captureStillPicture() {
        try {
            //final Activity activity = getActivity();
            if (null == this || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            //Apply Preview Settings to actuqal acquisition
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);

            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSetting);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);

            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) progressValueExposure * 1000000);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) progressValueExposure * 1000000);


            //captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Orientation
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                    cameraReady = true;
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    // AcquireActivity Settings
    private class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
            Log.e("mImageSize ", "Filename: " + String.valueOf(file) + " - Size: " + String.valueOf(mImage.getWidth() + " " + mImage.getHeight()));
        }

        @Override
        public void run() {

            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            /*Open crop activity
            Cropping library used: https://github.com/ArthurHub/Android-Image-Cropper
             */

            //capturedImgUri = Uri.fromFile(mFile);
            //CropImage.activity(capturedImgUri).setFixAspectRatio(true).setAspectRatio(12,5).setGuidelines(CropImageView.Guidelines.ON).start(getActivity());
        }
    }


    public void setAcqMethod(String acquireTypeFromSettings){
        acquireType = acquireTypeFromSettings;
        btnAcquire.setText(acquireType);

        if(g_illumination_processed & acquireType==DPCMode) mBitmap = getSegments(TF_result_after_NN, global_cx, global_cy, global_na);
        else if(acquireType==DPCMode) mBitmap = getDPC(global_cx, global_cy, 0, global_na);
        else if(acquireType==BFMode) mBitmap = getCircle(global_cx, global_cy, global_na);
        else if(acquireType==DFMode) mBitmap = getDarkfield(global_cx, global_cy, global_na, global_na+30);

        updatePresentation();



    }


    public void setMmCount(int SetmmCount){
        mmCount = SetmmCount;
    }




    /**
     * Displays the next color on the secondary screen if it is activate.
     * @param
     */
    private void showNextColor() {
        if (mPresentation != null) {
            // a second screen is active and initialized, show the next color
            mPresentation.setBitmap(mBitmap);

        }
    }




    }
