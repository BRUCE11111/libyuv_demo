/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.androidh264codecproject.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;


import com.example.androidh264codecproject.env.BorderedText;
import com.example.androidh264codecproject.env.ImageUtils;
import com.example.androidh264codecproject.env.Logger;
import com.example.androidh264codecproject.tflite.Classifier;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** A tracker that handles non-max suppression and matches existing objects to new detections. */
public class MultiBoxTracker {
  Lock lock = new ReentrantLock();

  //public Matrix source_matrix=getFrameToCanvasMatrix();

  public int source_width=0;

  public int source_height=0;
  public float multiplier=0;
  private static final float TEXT_SIZE_DIP = 18;
  private static final float MIN_SIZE = 16.0f;
  private static final int[] COLORS = {
    Color.BLUE,
    Color.RED,
    Color.GREEN,
    Color.YELLOW,
    Color.CYAN,
    Color.MAGENTA,
    Color.WHITE,
    Color.parseColor("#55FF55"),
    Color.parseColor("#FFA500"),
    Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"),
    Color.parseColor("#FFFFAA"),
    Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"),
    Color.parseColor("#0D0068")
  };
  public float[][][] array_person_point;
  public int person_num=0;

  public synchronized void set_person_num(int num){
  this.person_num=num;
  Log.i("person","  "+num+" xuxiaohui");
  }


  public synchronized void set_array_person_point(float[][][] array){
    this.array_person_point=array;
  }

  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();
  private final Logger logger = new Logger();
  private final Queue<Integer> availableColors = new LinkedList<Integer>();
  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();
  private final Paint boxPaint = new Paint();
  private final float textSizePx;
  private final BorderedText borderedText;
  public Matrix frameToCanvasMatrix;
  private int frameWidth;
  private int frameHeight;
  public int sensorOrientation;

  public MultiBoxTracker(final Context context) {
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(10.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  public synchronized void setFrameConfiguration(
      final int width, final int height, final int sensorOrientation) {
    frameWidth = width;
    frameHeight = height;
    this.sensorOrientation = sensorOrientation;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }
  }

  public synchronized void trackResults(final List<Classifier.Recognition> results, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(results);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void draw(final Canvas canvas) {
    lock.lock();

    final boolean rotated = sensorOrientation % 180 == 90;

    //boolean rotated=false;
    multiplier =
        Math.min(
            canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
            canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);


    for (final TrackedRecognition recognition : trackedObjects) {
      final RectF trackedPos = new RectF(recognition.location);

      getFrameToCanvasMatrix().mapRect(trackedPos);
      boxPaint.setColor(recognition.color);

      float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
      canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);

      final String labelString =
          !TextUtils.isEmpty(recognition.title)
              ? String.format("%s %.2f", recognition.title, (100 * recognition.detectionConfidence))
              : String.format("%.2f", (100 * recognition.detectionConfidence));

      borderedText.drawText(
          canvas, trackedPos.left + cornerSize, trackedPos.top, labelString + "%", boxPaint);
    }

    int time=0;
    for(int p=0;p<person_num;p++){

      time=0;
      for(int i=0;i<14;i++) {

        boxPaint.setColor(Color.YELLOW);
        float x = array_person_point[p][0][i];
        float y = array_person_point[p][1][i];
        if(x==0||y==0){
          continue;
        }

        boxPaint.setColor(COLORS[i]);

        canvas.drawCircle(x*multiplier, y*(multiplier), 5, boxPaint);


          array_person_point[p][0][i]=x*multiplier;
          array_person_point[p][1][i]=y*multiplier;

          Log.i("testxxh",time+++ " "+x*multiplier+" "+y*multiplier+" 屏幕 "+p);



      }
      boxPaint.setColor(Color.YELLOW);



      canvas.drawLine(array_person_point[p][0][0],array_person_point[p][1][0],
              array_person_point[p][0][1],array_person_point[p][1][1],boxPaint);
      canvas.drawLine(array_person_point[p][0][2],array_person_point[p][1][2],
              array_person_point[p][0][3],array_person_point[p][1][3],boxPaint);
      canvas.drawLine(array_person_point[p][0][3],array_person_point[p][1][3],array_person_point[p][0][4],array_person_point[p][1][4],boxPaint);
      canvas.drawLine(array_person_point[p][0][2],array_person_point[p][1][2],array_person_point[p][0][5],array_person_point[p][1][5],boxPaint);
      canvas.drawLine(array_person_point[p][0][5],array_person_point[p][1][5],array_person_point[p][0][6],array_person_point[p][1][6],boxPaint);
      canvas.drawLine(array_person_point[p][0][6],array_person_point[p][1][6],array_person_point[p][0][7],array_person_point[p][1][7],boxPaint);
      canvas.drawLine(array_person_point[p][0][11],array_person_point[p][1][11],array_person_point[p][0][12],array_person_point[p][1][12],boxPaint);
      canvas.drawLine(array_person_point[p][0][12],array_person_point[p][1][12],array_person_point[p][0][13],array_person_point[p][1][13],boxPaint);
      canvas.drawLine(array_person_point[p][0][8],array_person_point[p][1][8],array_person_point[p][0][9],array_person_point[p][1][9],boxPaint);
      canvas.drawLine(array_person_point[p][0][9],array_person_point[p][1][9],array_person_point[p][0][10],array_person_point[p][1][10],boxPaint);
      canvas.drawLine(array_person_point[p][0][8],array_person_point[p][1][8],array_person_point[p][0][11],array_person_point[p][1][11],boxPaint);
      canvas.drawLine(array_person_point[p][0][1],array_person_point[p][1][1],
              (array_person_point[p][0][8]+array_person_point[p][0][11])/2,
              (array_person_point[p][1][11]+array_person_point[p][0][8])/2,boxPaint);


    }

    lock.unlock();

  }

  private synchronized void processResults(final List<Classifier.Recognition> results) {
    final List<Pair<Float, Classifier.Recognition>> rectsToTrack = new LinkedList<Pair<Float, Classifier.Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Classifier.Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);

      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Classifier.Recognition>(result.getConfidence(), result));
    }

    trackedObjects.clear();
    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    for (final Pair<Float, Classifier.Recognition> potential : rectsToTrack) {
      final TrackedRecognition trackedRecognition = new TrackedRecognition();
      trackedRecognition.detectionConfidence = potential.first;
      trackedRecognition.location = new RectF(potential.second.getLocation());
      trackedRecognition.title = potential.second.getTitle();
      trackedRecognition.color = COLORS[trackedObjects.size()];
      trackedObjects.add(trackedRecognition);

      if (trackedObjects.size() >= COLORS.length) {
        break;
      }
    }
  }

  private static class TrackedRecognition {
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }
}
