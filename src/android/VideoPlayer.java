package com.moust.cordova.videoplayer;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.VideoView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VideoPlayer extends CordovaPlugin implements OnCompletionListener, OnPreparedListener, OnErrorListener, OnDismissListener {

  protected static final String LOG_TAG = "VideoPlayer";

  protected static final String ASSETS = "/android_asset/";

  private CallbackContext callbackContext = null;

  //private Dialog dialog;
  private List<Dialog> dialogs = new ArrayList<Dialog>();

  //private VideoView videoView;
  private List<VideoView> videoViews = new ArrayList<VideoView>();

  //private MediaPlayer player;
  private List<MediaPlayer> players = new ArrayList<MediaPlayer>();


  /**
   * Executes the request and returns PluginResult.
   *
   * @param action The action to execute.
   * @param args   JSONArray of arguments for the plugin.
   * @return A PluginResult object with a status and message.
   */
  public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
    if (action.equals("play")) {
      this.callbackContext = callbackContext;

      CordovaResourceApi resourceApi = webView.getResourceApi();
      JSONArray targets = args.getJSONArray(0);
      Log.v(LOG_TAG, targets.toString());
      final JSONObject options = args.getJSONObject(1);
      Iterator iterator = options.keys();
      final JSONArray optionsArray = new JSONArray();

      while (iterator.hasNext()) {
        String key = (String) iterator.next();
        optionsArray.put(options.get(key));
      }
      Log.v(LOG_TAG, optionsArray.toString());
      final String[] paths = new String[targets.length()];

      for (int i = 0; i < targets.length(); i++) {
        String fileUriStr;
        String target = targets.getString(i);
        try {
          Uri targetUri = resourceApi.remapUri(Uri.parse(target));
          fileUriStr = targetUri.toString();
        } catch (IllegalArgumentException e) {
          fileUriStr = target;
        }

        Log.v(LOG_TAG, fileUriStr);

        final String path = stripFileProtocol(fileUriStr);
        paths[i] = path;
      }

      // Create dialog in new thread
      cordova.getActivity().runOnUiThread(new Runnable() {
        public void run() {
          openVideoDialogs(paths, optionsArray);

        }
      });

      // Don't return any result now
      PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
      pluginResult.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginResult);
      callbackContext = null;

      return true;
    } else if (action.equals("close")) {
      Log.v(LOG_TAG, action);
      if (dialogs != null) {
        for (int i = 0; i < dialogs.toArray().length; i++) {
          MediaPlayer player = players.get(i);
          Dialog dialog = dialogs.get(i);
          if (player.isPlaying()) {
            player.stop();
          }
          player.release();
          dialog.dismiss();
        }
      }

      if (callbackContext != null) {
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        result.setKeepCallback(false); // release status callback in JS side
        callbackContext.sendPluginResult(result);
        callbackContext = null;
      }

      return true;
    }
    return false;
  }

  /**
   * Removes the "file://" prefix from the given URI string, if applicable.
   * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
   *
   * @param uriString the URI string to operate on
   * @return a path without the "file://" prefix
   */
  public static String stripFileProtocol(String uriString) {
    if (uriString.startsWith("file://")) {
      return Uri.parse(uriString).getPath();
    }
    return uriString;
  }


  @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
  protected void openVideoDialogs(String[] paths, JSONArray optionsArray) {
    dialogs = new ArrayList<Dialog>();
    videoViews = new ArrayList<VideoView>();
    players = new ArrayList<MediaPlayer>();

    JSONObject options = new JSONObject();
    for (int i = 0; i < paths.length; i++) {
      String path = paths[i];
      try {
        options = optionsArray.getJSONObject(i);
      } catch (JSONException jse) {
        Log.e(LOG_TAG, jse.getLocalizedMessage());
        jse.printStackTrace();
      }

      // Let's create the main dialog
      final Dialog dialog = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);
      dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
      dialog.setCancelable(true);
      dialog.setOnDismissListener(this);
      dialog.getWindow().setFlags(LayoutParams.FLAG_FULLSCREEN, LayoutParams.FLAG_FULLSCREEN);
      dialogs.add(dialog);

      // Main container layout
      final LinearLayout main = new LinearLayout(cordova.getActivity());
      main.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      main.setOrientation(LinearLayout.VERTICAL);


      final VideoView videoView = new VideoView(cordova.getActivity());
      videoView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      videoViews.add(videoView);
      main.addView(videoView);

      final MediaPlayer player = new MediaPlayer();
      player.setOnPreparedListener(this);
      player.setOnCompletionListener(this);
      player.setOnErrorListener(this);
      player.setLooping(true);
      players.add(player);

      if (path.startsWith(ASSETS)) {
        String f = path.substring(15);
        Log.v(LOG_TAG, "In path.startsWith(ASSETS) " + f);
        AssetFileDescriptor fd = null;
        try {
          fd = cordova.getActivity().getAssets().openFd(f);
          player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        } catch (Exception e) {
          Log.v(LOG_TAG, "In player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength())");
          e.printStackTrace(System.out);
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
          result.setKeepCallback(false); // release status callback in JS side
          callbackContext.sendPluginResult(result);
          callbackContext = null;
          return;
        }
      } else {
        try {
          player.setDataSource(path);
        } catch (Exception e) {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
          result.setKeepCallback(false); // release status callback in JS side
          callbackContext.sendPluginResult(result);
          callbackContext = null;
          return;
        }
      }

      try {
        float volume = Float.valueOf(options.getString("volume"));
        Log.d(LOG_TAG, "setVolume: " + volume);
        player.setVolume(volume, volume);
      } catch (Exception e) {
        PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
        result.setKeepCallback(false); // release status callback in JS side
        callbackContext.sendPluginResult(result);
        callbackContext = null;
        return;
      }

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
        try {
          int scalingMode = options.getInt("scalingMode");
          switch (scalingMode) {
            case MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING:
              Log.d(LOG_TAG, "setVideoScalingMode VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING");
              player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
              break;
            default:
              Log.d(LOG_TAG, "setVideoScalingMode VIDEO_SCALING_MODE_SCALE_TO_FIT");
              player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
          }
        } catch (Exception e) {
          PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
          result.setKeepCallback(false); // release status callback in JS side
          callbackContext.sendPluginResult(result);
          callbackContext = null;
          return;
        }
      }

      final SurfaceHolder mHolder = videoView.getHolder();
      mHolder.setKeepScreenOn(true);
      mHolder.addCallback(new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
          player.setDisplay(holder);
          try {
            player.prepare();
          } catch (Exception e) {
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
          }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
          player.release();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }
      });


      WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
      lp.copyFrom(dialog.getWindow().getAttributes());
      float density = cordova.getActivity().getResources().getDisplayMetrics().density;
      int width = cordova.getActivity().getResources().getDisplayMetrics().widthPixels;
      int height = cordova.getActivity().getResources().getDisplayMetrics().heightPixels;
      int widthCenter = width / 2;
      int heightCenter = height / 2;
      try {

        int videoWidth = (int) (Math.round(options.getDouble("width")) * density);
        int videoHeight = (int) (Math.round(options.getDouble("height")) * density);
        int videoWidthCenter = videoWidth / 2;
        int videoHeightCenter = videoHeight / 2;
        double left = Math.round(options.getDouble("left")) * density;
        double top = Math.round(options.getDouble("top")) * density;
        lp.width = videoWidth;
        lp.height = videoHeight;
        int x = (int) (-widthCenter + left + videoWidthCenter);
        int y = (int) (-heightCenter + top + videoHeightCenter);
        lp.x = left > widthCenter ? Math.abs(x) : -Math.abs(x);
        lp.y = top > heightCenter ? Math.abs(y) : -Math.abs(y);
        Log.v(LOG_TAG, "x :" + lp.x);
        Log.v(LOG_TAG, "left :" + left);

        Log.v(LOG_TAG, "y :" + lp.y);
        Log.v(LOG_TAG, "top :" + top);

      } catch (JSONException jse) {

      }
      dialog.setContentView(main);
      dialog.show();
      dialog.getWindow().setAttributes(lp);
    }
  }

  @Override
  public boolean onError(MediaPlayer mp, int what, int extra) {
    Log.e(LOG_TAG, "MediaPlayer.onError(" + what + ", " + extra + ")");
    if (mp.isPlaying()) {
      mp.stop();
    }
    mp.release();
    int index = players.indexOf(mp);
    dialogs.get(index).dismiss();
    return false;
  }

  @Override
  public void onPrepared(MediaPlayer mp) {
    mp.start();
  }

  @Override
  public void onCompletion(MediaPlayer mp) {
    Log.d(LOG_TAG, "MediaPlayer completed");
    mp.release();
    int index = players.indexOf(mp);
    dialogs.get(index).dismiss();
  }

  @Override
  public void onDismiss(DialogInterface dialog) {
    Log.d(LOG_TAG, "Dialog dismissed");
    if (callbackContext != null) {
      PluginResult result = new PluginResult(PluginResult.Status.OK);
      result.setKeepCallback(false); // release status callback in JS side
      callbackContext.sendPluginResult(result);
      callbackContext = null;
    }
  }
}
