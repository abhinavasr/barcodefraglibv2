/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import java.io.ByteArrayOutputStream;
import java.util.Hashtable;
import java.util.Map;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.abhi.barcode.frag.libv2.BarcodeFragment;
import com.abhi.barcode.frag.libv2.IDS;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.detector.Detector;

final class DecodeHandler extends Handler {

  private static final String TAG = DecodeHandler.class.getSimpleName();

  private final BarcodeFragment activity;
  private final MultiFormatReader multiFormatReader;
  private boolean running = true;
  private final Decoder decoder;
  private final Map<DecodeHintType,Object> _hints;

  DecodeHandler(BarcodeFragment activity, Map<DecodeHintType,Object> hints) {
    multiFormatReader = new MultiFormatReader();
    multiFormatReader.setHints(hints);
    this._hints = hints;
    this.activity = activity;
    this.decoder = new Decoder();
  }

  @Override
  public void handleMessage(Message message) {
    if (!running) {
      return;
    }
    switch (message.what) {
      case IDS.id.decode:
        decode((byte[]) message.obj, message.arg1, message.arg2);
        break;
      case IDS.id.quit:
        running = false;
        Looper.myLooper().quit();
        break;
    }
  }

  /**
   * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
   * reuse the same reader objects from one decode to the next.
   *
   * @param data   The YUV preview frame.
   * @param width  The width of the preview frame.
   * @param height The height of the preview frame.
   */
  private void decode(byte[] data, int width, int height) {
    final long startTime = System.currentTimeMillis();
    Result result = null;
    PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
    if (source != null) {
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      try {
        result = decode(bitmap);
        //result = getResultFromBinaryBitmap(bitmap);
      } catch (ReaderException re) {
        // continue
      } finally {
        multiFormatReader.reset();
      }
    }
    sendMessage(result, source, startTime);
  }

  private Result getResultFromBinaryBitmap(BinaryBitmap bitmap) throws NotFoundException, FormatException {
    BitMatrix bitMatrix = bitmap.getBlackMatrix();
    Detector detector = new Detector(bitMatrix);
    detector.detect().getBits();
    return multiFormatReader.decodeWithState(bitmap);
  }

  private void sendMessage(Result result, PlanarYUVLuminanceSource source, long startTime) {
    Handler handler = activity.getHandler();
    if (result != null) {
      // Don't log the barcode contents for security.
      long endTime = System.currentTimeMillis();
      Log.d(TAG, "Found barcode in " + (endTime - startTime) + " ms");
      if (handler != null) {
        Message message = Message.obtain(handler, IDS.id.decode_succeeded, result);
        Bundle bundle = new Bundle();
        bundleThumbnail(source, bundle);
        message.setData(bundle);
        message.sendToTarget();
      }
    } else {
      if (handler != null) {
        Message message = Message.obtain(handler, IDS.id.decode_failed);
        message.sendToTarget();
      }
    }
  }

  public Result decode(BinaryBitmap image /*, Hashtable hints */) throws NotFoundException, ChecksumException, FormatException {
    DecoderResult decoderResult;
    ResultPoint[] points;
//    if (hints != null && hints.containsKey(DecodeHintType.PURE_BARCODE)) {
//      BitMatrix bits=extractPureBits(image.getBlackMatrix());
//      decoderResult=decoder.decode(bits,hints);
//      points=NO_POINTS;
//    }
//    else {
      DetectorResult detectorResult=new Detector(image.getBlackMatrix()).detect(_hints);
      decoderResult=decoder.decode(detectorResult.getBits(),_hints);
      points=detectorResult.getPoints();
//    }
    Result result=new Result(decoderResult.getText(), decoderResult.getRawBytes(), points, BarcodeFormat.QR_CODE);
    if (decoderResult.getByteSegments() != null) {
      result.putMetadata(ResultMetadataType.BYTE_SEGMENTS,decoderResult.getByteSegments());
    }
    if (decoderResult.getECLevel() != null) {
      result.putMetadata(ResultMetadataType.ERROR_CORRECTION_LEVEL,decoderResult.getECLevel().toString());
    }

    return result;
  }

  private static void bundleThumbnail(PlanarYUVLuminanceSource source, Bundle bundle) {
    int[] pixels = source.renderThumbnail();
    int width = source.getThumbnailWidth();
    int height = source.getThumbnailHeight();
    Bitmap bitmap = Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream out = new ByteArrayOutputStream();    
    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
    bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
    bundle.putFloat(DecodeThread.BARCODE_SCALED_FACTOR, (float) width / source.getWidth());
  }

}
