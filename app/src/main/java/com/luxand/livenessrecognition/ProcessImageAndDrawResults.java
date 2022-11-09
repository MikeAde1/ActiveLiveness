package com.luxand.livenessrecognition;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import com.luxand.FSDK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//  graphics on top of the video
class ProcessImageAndDrawResults extends View {
    public FSDK.HTracker mTracker;

    final int MAX_FACES = 5;
    int mStopping;
    int mStopped;

    Context mContext;
    byte[] mYUVData;
    byte[] mRGBData;
    int mImageWidth, mImageHeight;
    boolean first_frame_saved;
    boolean rotated;


    ButtonEvents buttonEvents;

    final Lock faceLock = new ReentrantLock();

    FSDK.HImage image = new FSDK.HImage();
    FSDK.HImage rotatedImage = new FSDK.HImage();

    FSDK.FSDK_IMAGEMODE image_mode = new FSDK.FSDK_IMAGEMODE() {{
        mode = FSDK.FSDK_IMAGEMODE.FSDK_IMAGE_COLOR_24BIT;
    }};

    final long[] IDs = new long[MAX_FACES];
    final long[] face_count = new long[1];
    final ArrayList<Long> missed = new ArrayList<>();

    final Map<Long, FaceLocator> trackers = new HashMap<>();


    public ProcessImageAndDrawResults(Context context, ButtonEvents buttonEvents) {
        super(context);

        mStopping = 0;
        mStopped = 0;
        rotated = false;
        mContext = context;

        //mBitmap = null;
        mYUVData = null;
        mRGBData = null;

        this.buttonEvents = buttonEvents;

        first_frame_saved = false;
    }

    private static boolean contains(long[] ids, long id) {
        for (long di : ids)
            if (id == di)
                return true;
        return false;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        if (mStopping == 1) {
            mStopped = 1;
            super.onDraw(canvas);
            return;
        }

        if (mYUVData == null) {
            super.onDraw(canvas);
            return; //nothing to process
        }

        int canvasWidth = getWidth();
        //int canvasHeight = canvas.getHeight();

        // Convert from YUV to RGB
        decodeYUV420SP(mRGBData, mYUVData, mImageWidth, mImageHeight);

        // Load image to FaceSDK
        FSDK.LoadImageFromBuffer(image, mRGBData, mImageWidth, mImageHeight, mImageWidth * 3, image_mode);
        FSDK.MirrorImage(image, false);

        //it is necessary to work with local variables (onDraw called not the time when mImageWidth,... being reassigned, so swapping mImageWidth and mImageHeight may be not safe)
        int ImageWidth = mImageWidth;
        //int ImageHeight = mImageHeight;
        if (rotated) {
            //noinspection SuspiciousNameCombination
            ImageWidth = mImageHeight;
            //ImageHeight = mImageWidth;

            FSDK.CreateEmptyImage(rotatedImage);
            FSDK.RotateImage90(image, -1, rotatedImage);
            FSDK.FreeImage(image);

            FSDK.HImage tmp = image;
            image = rotatedImage;
            rotatedImage = tmp;
        }

        // Save first frame to gallery to debug (e.g. rotation angle)
        /*
        if (!first_frame_saved) {
            first_frame_saved = true;
            String galleryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
            FSDK.SaveImageToFile(RotatedImage, galleryPath + "/first_frame.jpg"); //frame is rotated!
        }
        */

        Arrays.fill(IDs, 0); //fill all the values with 0
        FSDK.FeedFrame(mTracker, 0, image, face_count, IDs);
        FSDK.FreeImage(image);

        float ratio = (canvasWidth * 1.0f) / ImageWidth;

        faceLock.lock();

        // Mark and name faces
        for (int i = 0; i < face_count[0]; ++i)
            if (!trackers.containsKey(IDs[i]))
                trackers.put(IDs[i], new FaceLocator(IDs[i], mTracker, ratio));

        missed.clear();
        for (Map.Entry<Long, FaceLocator> entry : trackers.entrySet())
            if (contains(IDs, entry.getKey()))
                entry.getValue().draw(canvas, buttonEvents);
            else
                missed.add(entry.getKey());

        for (long id : missed) {
            FaceLocator st = trackers.get(id);

            for (int i = 0; i < face_count[0]; ++i)
                if (IDs[i] != id && st.doesIntersect(trackers.get(IDs[i]))) {
                    trackers.remove(id);
                    break;
                }

            if (trackers.containsKey(id) && !st.draw(canvas, buttonEvents))
                trackers.remove(id);
        }

        faceLock.unlock();

        super.onDraw(canvas);
    } // end onDraw method


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) { //NOTE: the method can be implemented in Preview class
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            for (FaceLocator loc : trackers.values())
                if (loc.isInside(x, y)) {
                    if (!loc.isActive())
                        loc.setActive(true);
                } else
                    loc.setActive(false);
        }
        return true;
    }

    static public void decodeYUV420SP(byte[] rgb, byte[] yuv420sp, int width, int height) {
        final int frameSize = width * height;
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0) y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }
                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0) r = 0;
                else if (r > 262143) r = 262143;
                if (g < 0) g = 0;
                else if (g > 262143) g = 262143;
                if (b < 0) b = 0;
                else if (b > 262143) b = 262143;

                rgb[3 * yp] = (byte) ((r >> 10) & 0xff);
                rgb[3 * yp + 1] = (byte) ((g >> 10) & 0xff);
                rgb[3 * yp + 2] = (byte) ((b >> 10) & 0xff);
                ++yp;
            }
        }
    }
} // end of ProcessImageAndDrawResults class
