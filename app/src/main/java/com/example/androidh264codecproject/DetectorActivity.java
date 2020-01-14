/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidh264codecproject;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;

import android.graphics.Matrix;

import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;

import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;


import com.example.androidh264codecproject.customview.OverlayView;
import com.example.androidh264codecproject.env.BorderedText;
import com.example.androidh264codecproject.env.ImageUtils;
import com.example.androidh264codecproject.env.Logger;
import com.example.androidh264codecproject.tflite.Classifier;
import com.example.androidh264codecproject.tflite.Classifier.Recognition;
import com.example.androidh264codecproject.tflite.TFLiteObjectDetectionAPIModel;
import com.example.androidh264codecproject.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


import static java.lang.StrictMath.abs;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {



  public int source_width=0;
  public int source_height=0;
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.

  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;


  private boolean computingDetection = false;

  @Override
  public void onImageAvailable(ImageReader reader) {
    super.onImageAvailable(reader);
  }

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;


/*
*
* ____________________________________
*
* */
private int person_predict_picture_width=192;
private long person_predict_time=0;


private Runnable periodicClasssify=new Runnable() {

  Bitmap bitmap=null;
  public void etbitmap(Bitmap bitmap){
    this.bitmap=bitmap;
  }

  @Override
  public void run() {
    classifyFrame(bitmap);
  }
};


public String classifyFrame(Bitmap bitmap){

if(this.classifier==null){
  Log.i("classifyFramenull","null");
  return null;
}
String result=classifier.classifyFrame(bitmap);
if(result==null){
  Log.i("person","persondetect is null");
}
bitmap.recycle();
return result;

}


  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    //屏幕适配，单位转换，参数为想要得到的单位，第二个参数是想要得到的数值，第三个参数是显示区域的各种属性值。
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
          TFLiteObjectDetectionAPIModel.create(
              getAssets(),
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
          Toast.makeText(
              getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    Log.d("rotation"," rotation: "+rotation+" getscreenorientation: "+getScreenOrientation());
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);


    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new OverlayView.DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            source_height=canvas.getHeight();
            source_width=canvas.getWidth();
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }



  @Override
  protected synchronized void processImage() {

    ++timestamp;
    final long currTimestamp = timestamp;
    //trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    //Bitmap temp_bitmap=croppedBitmap.copy(cropCopyBitmap.getConfig(),true);

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }


            LOGGER.i("Running detection on image " + currTimestamp);

            final long startTime = SystemClock.uptimeMillis();
            final List<Recognition> results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
           // cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);


            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            final List<Recognition> mappedRecognitions =
                new LinkedList<Recognition>();
            int len_results=results.size();



            int for_time=0;

            int ratio_person_picture[][]=new int[len_results][4];
            float[][][] array_point=new float[len_results][2][14];
            long time_person_predicte=0;


            for (final Recognition result : results) {

              final RectF location = result.getLocation();

              int left=(int)location.left;
              int right=(int)location.right;
              int bottom=(int)location.bottom;
              int top=(int)location.top;


              if(left<0){
                left=0;
              }

              left=left>croppedBitmap.getWidth()?croppedBitmap.getWidth():left;


              if(right<0){
                right=0;
              }

              right=right>croppedBitmap.getWidth()?croppedBitmap.getWidth():right;


              if(bottom<0){
                bottom=0;
              }

              bottom=bottom>croppedBitmap.getHeight()?croppedBitmap.getHeight():bottom;

              if(top<0){
                top=0;
              }

              top=top>croppedBitmap.getHeight()?croppedBitmap.getHeight():top;

              Log.i("recf",location.bottom+"  bottom "+location.top+" top "+location.left+" left "+location.right+" right ");
              Log.i("recf",croppedBitmap.getHeight()+" height "+croppedBitmap.getWidth());

              if (location != null && result.getConfidence() >= minimumConfidence  &&result.getTitle().equals("person")) {

                cropToFrameTransform.mapRect(location);
                if(location.height()<16.0f||location.width()<16.0f){
                  continue;
                }

                //xuxiaohui add the select square to save the picture code

                Bitmap mBitmapSelectBitmap = croppedBitmap.createBitmap(croppedBitmap,(int)(left<right?left:right),
                        (int)(top<bottom?top:bottom), (int)abs(right-left),(int) abs(top-bottom));

                int ratio_x=abs(right-left);
                int ratio_y=abs(top-bottom);
                int start_x=(left<right?left:right);
                int start_y=(top<bottom?top:bottom);
                ratio_person_picture[for_time][2]=ratio_x;
                ratio_person_picture[for_time][3]=ratio_y;
                ratio_person_picture[for_time][0]=start_x;
                ratio_person_picture[for_time][1]=start_y;

                result.setLocation(location);
                mappedRecognitions.add(result);

                Bitmap rebitmap=Bitmap.createScaledBitmap(mBitmapSelectBitmap,192,192,true);

                String result_process_time=classifyFrame(rebitmap);
                array_point[for_time]=classifier.getPointArray();
                array_point[for_time]=person_point_process(array_point[for_time],ratio_person_picture[for_time][0],
                        ratio_person_picture[for_time][1],ratio_person_picture[for_time][2],
                        ratio_person_picture[for_time][3]);

                time_person_predicte=Long.parseLong(result_process_time);
                time_person_predicte+=time_person_predicte;
                person_predict_time=time_person_predicte;
                for_time++;
              }
            }

            tracker.set_person_num(for_time);
            tracker.set_array_person_point(array_point);
            tracker.trackResults(mappedRecognitions, currTimestamp);
            trackingOverlay.postInvalidate();

            computingDetection = false;

            runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {

                    showFrameInfo(previewWidth + "x" + previewHeight);
                    showCropInfo(source_width + "x" + source_height);
                    showInference("OD探测"+lastProcessingTimeMs +"ms "+"人体"+person_predict_time+ " ms");
                  }
                });

  }

  public float[][] person_point_process(float[][] array_float,int start_x,int start_y,int width,int height) {

    int flag=0;
    for (int i = 0; i < 14; i++) {
      float x = array_float[0][i];
      float y = array_float[1][i];

      Log.d("point0",flag+" "+x+" "+y);

      float ratio_x=previewWidth/300;
      float ratio_y=previewHeight/300;

      float ratio_48=192/48;
      x=x*ratio_48;
      y=y*ratio_48;//还原为192

      Log.d("point48",flag+" "+x+" "+y);


      x=(width*x)/person_predict_picture_width;//还原为300中的框子
      y=(height*y)/person_predict_picture_width;

      Log.d("pointmid",flag+" "+x+" "+y+" height:"+height+" width"+width);

      if(x!=0&&y!=0){
        x=x+start_x;
        y=y+start_y;//还原为300*300
      }


      Log.i("point300",flag + " "+x+" "+y);

      x=x*((previewWidth/300));//640*480
      y=y*(previewHeight/300);



        array_float[0][i]=x;
        array_float[1][i]=y;

      Log.i("testxxh",flag++ + " "+x+" "+y+" 640*480");


    }
    return array_float;
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> detector.setUseNNAPI(isChecked));
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }
}
