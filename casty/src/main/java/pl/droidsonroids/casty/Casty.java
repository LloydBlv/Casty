package pl.droidsonroids.casty;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import timber.log.Timber;

/**
 * Core class of Casty. It manages buttons/widgets and gives access to the media player.
 */
public class Casty implements CastyPlayer.OnMediaLoadedListener {
  private final static String TAG = "Casty";
  static String receiverId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
  static CastOptions customCastOptions;

  private SessionManagerListener<CastSession> sessionManagerListener;
  private OnConnectChangeListener onConnectChangeListener;
  private OnCastSessionUpdatedListener onCastSessionUpdatedListener;

  private RemoteMediaClient.ProgressListener mMediaProgressListener;

  private CastSession castSession;
  private CastyPlayer castyPlayer;
  private Activity activity;
  private IntroductoryOverlay introductionOverlay;

  public int mProgressListenerPeriod = 0;

  /**
   * Sets the custom receiver ID. Should be used in the {@link Application} class.
   *
   * @param receiverId the custom receiver ID, e.g. Styled Media Receiver - with custom logo and
   * background
   */
  public static void configure(@NonNull String receiverId) {
    Casty.receiverId = receiverId;
  }

  /**
   * Sets the custom CastOptions, should be used in the {@link Application} class.
   *
   * @param castOptions the custom CastOptions object, must include a receiver ID
   */
  public static void configure(@NonNull CastOptions castOptions) {
    Casty.customCastOptions = castOptions;
  }

  /**
   * Creates the Casty object.
   *
   * @param activity {@link Activity} in which Casty object is created
   * @return the Casty object
   */
  public static Casty create(@NonNull Activity activity,
      final RemoteMediaClient.ProgressListener progressListener) {
    int playServicesState =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
    if (playServicesState == ConnectionResult.SUCCESS) {
      return new Casty(activity, progressListener);
    } else {
      Log.w(Casty.TAG, "Google Play services not found on a device, Casty won't work.");
      return new CastyNoOp();
    }
  }

  /**
   * Creates the Casty object.
   *
   * @param activity {@link Activity} in which Casty object is created
   * @return the Casty object
   */
  public static Casty create(@NonNull Activity activity,
      final OnConnectChangeListener onConnectChangeListener,
      final RemoteMediaClient.ProgressListener progressListener) {
    int playServicesState =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
    if (playServicesState == ConnectionResult.SUCCESS) {
      return new Casty(activity, progressListener, onConnectChangeListener);
    } else {
      Log.w(Casty.TAG, "Google Play services not found on a device, Casty won't work.");
      return new CastyNoOp();
    }
  }

  //Needed for NoOp instance
  Casty() {
    //no-op
  }

  private Casty(@NonNull Activity activity,
      final RemoteMediaClient.ProgressListener progressListener) {
    this.activity = activity;
    sessionManagerListener = createSessionManagerListener();
    this.mMediaProgressListener = progressListener;
    //mMediaProgressListener = new RemoteMediaClient.ProgressListener() {
    //    @Override public void onProgressUpdated(long progressMs, long durationMs) {
    //        Timber.d("onProgressUpdated(), progressMs:[%s], durationMs:[%s]", progressMs, durationMs);
    //
    //    }
    //};
    castyPlayer = new CastyPlayer(this);
    activity.getApplication().registerActivityLifecycleCallbacks(createActivityCallbacks());
    CastContext.getSharedInstance(activity).addCastStateListener(createCastStateListener());
    handleCurrentCastSession();
    registerSessionManagerListener();
  }

  private Casty(@NonNull final Activity activity,
      final RemoteMediaClient.ProgressListener progressListener,
      final OnConnectChangeListener onConnectChangeListener) {
    this.activity = activity;
    sessionManagerListener = createSessionManagerListener();
    this.mMediaProgressListener = progressListener;
    this.onConnectChangeListener = onConnectChangeListener;
    //mMediaProgressListener = new RemoteMediaClient.ProgressListener() {
    //    @Override public void onProgressUpdated(long progressMs, long durationMs) {
    //        Timber.d("onProgressUpdated(), progressMs:[%s], durationMs:[%s]", progressMs, durationMs);
    //
    //    }
    //};
    castyPlayer = new CastyPlayer(this);
    activity.getApplication().registerActivityLifecycleCallbacks(createActivityCallbacks());
    CastContext.getSharedInstance(activity).addCastStateListener(createCastStateListener());
    handleCurrentCastSession();
    registerSessionManagerListener();
  }

  /**
   * Gives access to {@link CastyPlayer}, which allows to control the media files.
   *
   * @return the instance of {@link CastyPlayer}
   */
  public CastyPlayer getPlayer() {
    return castyPlayer;
  }

  /**
   * Checks if a Google Cast device is connected.
   *
   * @return true if a Google Cast is connected, false otherwise
   */
  public boolean isConnected() {
    return castSession != null;
  }

  /**
   * Adds the discovery menu item on a toolbar and creates Introduction Overlay
   * Should be used in {@link Activity#onCreateOptionsMenu(Menu)}.
   *
   * @param menu Menu in which MenuItem should be added
   */
  @UiThread public void addMediaRouteMenuItem(@NonNull Menu menu) {
    activity.getMenuInflater().inflate(R.menu.casty_discovery, menu);
    setUpMediaRouteMenuItem(menu);
    MenuItem menuItem = menu.findItem(R.id.casty_media_route_menu_item);
    introductionOverlay = createIntroductionOverlay(menuItem);
  }

  /**
   * Makes {@link MediaRouteButton} react to discovery events.
   * Must be run on UiThread.
   *
   * @param mediaRouteButton Button to be set up
   */
  @UiThread public void setUpMediaRouteButton(@NonNull MediaRouteButton mediaRouteButton) {
    CastButtonFactory.setUpMediaRouteButton(activity, mediaRouteButton);
    //introductionOverlay = createIntroductionOverlay(mediaRouteButton);
  }

  /**
   * Adds the Mini Controller at the bottom of Activity's layout.
   * Must be run on UiThread.
   *
   * @return the Casty instance
   */
  @UiThread public Casty withMiniController() {
    addMiniController();
    return this;
  }

  /**
   * Adds the Mini Controller at the bottom of Activity's layout
   * Must be run on UiThread.
   */
  @UiThread public void addMiniController() {
    ViewGroup contentView = (ViewGroup) activity.findViewById(android.R.id.content);
    View rootView = contentView.getChildAt(0);
    LinearLayout linearLayout = new LinearLayout(activity);
    LinearLayout.LayoutParams linearLayoutParams =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    linearLayout.setOrientation(LinearLayout.VERTICAL);
    linearLayout.setLayoutParams(linearLayoutParams);

    contentView.removeView(rootView);

    ViewGroup.LayoutParams oldRootParams = rootView.getLayoutParams();
    LinearLayout.LayoutParams rootParams =
        new LinearLayout.LayoutParams(oldRootParams.width, 0, 1f);
    rootView.setLayoutParams(rootParams);

    linearLayout.addView(rootView);
    activity.getLayoutInflater().inflate(R.layout.mini_controller, linearLayout, true);
    activity.setContentView(linearLayout);
  }

  /**
   * Sets {@link OnConnectChangeListener}
   *
   * @param onConnectChangeListener Connect change callback
   */
  public void setOnConnectChangeListener(
      @Nullable OnConnectChangeListener onConnectChangeListener) {
    Timber.d("setOnConnectChangeListener(), onConnectChangeListener:[%s]", onConnectChangeListener);
    this.onConnectChangeListener = onConnectChangeListener;
  }

  /**
   * Sets {@link OnCastSessionUpdatedListener}
   *
   * @param onCastSessionUpdatedListener Cast session updated callback
   */
  public void setOnCastSessionUpdatedListener(
      @Nullable OnCastSessionUpdatedListener onCastSessionUpdatedListener) {
    this.onCastSessionUpdatedListener = onCastSessionUpdatedListener;
  }

  private void setUpMediaRouteMenuItem(Menu menu) {
    CastButtonFactory.setUpMediaRouteButton(activity, menu, R.id.casty_media_route_menu_item);
  }

  private String getReadableState(int state) {
    switch (state) {
      case CastState.CONNECTED:
        return "CONNECTED";
      case CastState.CONNECTING:
        return "CONNECTING";
      case CastState.NO_DEVICES_AVAILABLE:
        return "NO_DEVICES_AVAILABLE";
      case CastState.NOT_CONNECTED:
        return "NOT_CONNECTED";
      default:
        return state + "";
    }
  }

  @NonNull private CastStateListener createCastStateListener() {
    return new CastStateListener() {
      @Override public void onCastStateChanged(int state) {
        Timber.d("onCastStateChanged:[%s]", getReadableState(state));
        if (state != CastState.NO_DEVICES_AVAILABLE && introductionOverlay != null) {
          showIntroductionOverlay();
        }
      }
    };
  }

  private void showIntroductionOverlay() {
    introductionOverlay.show();
  }

  private SessionManagerListener<CastSession> createSessionManagerListener() {
    return new SessionManagerListener<CastSession>() {
      @Override public void onSessionStarted(CastSession castSession, String sessionId) {
        Timber.d("onSessionStarted(), castSession:[%s], sessionId:[%s]", castSession, sessionId);
        activity.invalidateOptionsMenu();
        onConnected(castSession);
      }

      @Override public void onSessionEnded(CastSession castSession, int error) {
        Timber.d("onSessionEnded(), castSession:[%s], error:[%s]", castSession, error);
        activity.invalidateOptionsMenu();
        onDisconnected(error);
      }

      @Override public void onSessionResumed(CastSession castSession, boolean wasSuspended) {
        Timber.d("onSessionResumed(), castSession");
        activity.invalidateOptionsMenu();
        onConnected(castSession);
      }

      @Override public void onSessionStarting(CastSession castSession) {
        Timber.d("onSessionStarting(), castSession:[%s]", castSession);
        if (onConnectChangeListener != null) onConnectChangeListener.onConnecting();

        //no-op
      }

      @Override public void onSessionStartFailed(CastSession castSession, int error) {
        Timber.d("onSessionStartFailed(), castSession:[%s], error:[%s]", castSession, error);
        if (onConnectChangeListener != null) onConnectChangeListener.onStartFailed(error);

        //no-op
      }

      @Override public void onSessionEnding(CastSession castSession) {
        Timber.d("onSessionEnding(), castSession:[%s]", castSession);

        //no-op
      }

      @Override public void onSessionResuming(CastSession castSession, String sessionId) {
        Timber.d("onSessionResuming(), castSession:[%s], sessionId:[%s]", castSession, sessionId);

        //no-op
      }

      @Override public void onSessionResumeFailed(CastSession castSession, int error) {
        Timber.d("onSessionResumeFailed(), castSession:[%s], error:[%s]", castSession, error);

        if (onConnectChangeListener != null) onConnectChangeListener.onStartFailed(error);

        //no-op
      }

      @Override public void onSessionSuspended(CastSession castSession, int error) {
        Timber.d("onSessionSuspended(), castSession:[%s], error:[%s]", castSession, error);

        //no-op
      }
    };
  }

  private void onConnected(CastSession castSession) {
    Timber.d(
        "onConnected(), castSession:[%s], onConnectChangeListener:[%s], onCastSessionUpdatedListener:[%s]",
        castSession, onConnectChangeListener, onCastSessionUpdatedListener);
    this.castSession = castSession;
    castyPlayer.setRemoteMediaClient(castSession.getRemoteMediaClient());

    registerProgressListener();

    if (onConnectChangeListener != null) onConnectChangeListener.onConnected();
    if (onCastSessionUpdatedListener != null) {
      onCastSessionUpdatedListener.onCastSessionUpdated(castSession);
    }
  }

  public void registerProgressListener() {
    Timber.d("registerProgressListener(), mProgressListenerPeriod:[%s], castSession:[%s]",
        mProgressListenerPeriod, castSession);
    if (mProgressListenerPeriod > 0
        && castSession != null
        && castSession.getRemoteMediaClient() != null) {
      castSession.getRemoteMediaClient()
          .addProgressListener(mMediaProgressListener, mProgressListenerPeriod);
    }
  }

  public void unregisterProgressListener() {
    Timber.d("unregisterProgressListener(), mProgressListenerPeriod:[%s], castSession:[%s]",
        mProgressListenerPeriod, castSession);

    if (mProgressListenerPeriod > 0
        && castSession != null
        && castSession.getRemoteMediaClient() != null) {
      this.castSession.getRemoteMediaClient().removeProgressListener(mMediaProgressListener);
    }
  }

  private void onDisconnected(final int error) {
    Timber.d("onDisconnected(), error:[%s]", CastStatusCodes.getStatusCodeString(error));
    unregisterProgressListener();

    this.castSession = null;
    if (onConnectChangeListener != null) onConnectChangeListener.onDisconnected(error);
    if (onCastSessionUpdatedListener != null) {
      onCastSessionUpdatedListener.onCastSessionUpdated(null);
    }
  }

  private Application.ActivityLifecycleCallbacks createActivityCallbacks() {
    return new Application.ActivityLifecycleCallbacks() {
      @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        //no-op
      }

      @Override public void onActivityStarted(Activity activity) {
        //no-op
      }

      @Override public void onActivityResumed(Activity activity) {
        if (Casty.this.activity == activity) {
          handleCurrentCastSession();
          registerSessionManagerListener();
        }
      }

      @Override public void onActivityPaused(Activity activity) {
        if (Casty.this.activity == activity) unregisterSessionManagerListener();
      }

      @Override public void onActivityStopped(Activity activity) {
        //no-op
      }

      @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        //no-op
      }

      @Override public void onActivityDestroyed(Activity activity) {
        if (Casty.this.activity == activity) {
          activity.getApplication().unregisterActivityLifecycleCallbacks(this);
        }
      }
    };
  }

  private IntroductoryOverlay createIntroductionOverlay(MenuItem menuItem) {
    return new IntroductoryOverlay.Builder(activity, menuItem).setTitleText(
        R.string.casty_introduction_text).setSingleTime().build();
  }

  private IntroductoryOverlay createIntroductionOverlay(MediaRouteButton mediaRouteButton) {
    return new IntroductoryOverlay.Builder(activity, mediaRouteButton).setTitleText(
        R.string.casty_introduction_text).setSingleTime().build();
  }

  public MediaInfo getMediaInfo() {
    /*fun Casty.getCurrentMediaInfo(context: Context): MediaInfo? {
    return CastContext.getSharedInstance(context)?.sessionManager?.currentCastSession?.remoteMediaClient?.mediaInfo
}*/
    try {
      return CastContext.getSharedInstance(activity)
          .getSessionManager()
          .getCurrentCastSession()
          .getRemoteMediaClient()
          .getMediaInfo();
    } catch (Exception ex) {
      return null;
    }
  }

  private void registerSessionManagerListener() {
    CastContext.getSharedInstance(activity)
        .getSessionManager()
        .addSessionManagerListener(sessionManagerListener, CastSession.class);
  }

  private void unregisterSessionManagerListener() {
    CastContext.getSharedInstance(activity)
        .getSessionManager()
        .removeSessionManagerListener(sessionManagerListener, CastSession.class);
  }

  private void handleCurrentCastSession() {
    CastSession newCastSession =
        CastContext.getSharedInstance(activity).getSessionManager().getCurrentCastSession();
    if (castSession == null) {
      if (newCastSession != null) {
        onConnected(newCastSession);
      }
    } else {
      if (newCastSession == null) {
        onDisconnected(-1);
      } else if (newCastSession != castSession) {
        onConnected(newCastSession);
      }
    }
  }

  @Override public void onMediaLoaded() {
    Timber.d("onMediaLoaded()");
    //startExpandedControlsActivity();
    onConnectChangeListener.onMediaLoaded();
  }

  public void startExpandedControlsActivity() {
    Intent intent = new Intent();
    intent.setComponent(new ComponentName(activity.getPackageName(), CastContext.getSharedInstance(activity)
        .getCastOptions()
        .getCastMediaOptions()
        .getExpandedControllerActivityClassName()));
    //Intent intent = new Intent(activity, CastContext.getSharedInstance(activity).getCastOptions().getCastMediaOptions().getExpandedControllerActivityClassName());
    //Intent intent = new Intent(activity, ExpandedControlsActivity.class);
    activity.startActivity(intent);
  }

  public interface OnConnectChangeListener {
    void onConnected();

    void onDisconnected(final int errorCode);

    void onConnecting();

    void onStartFailed(final int errorCode);

    void onMediaLoaded();
  }

  public interface OnCastSessionUpdatedListener {
    void onCastSessionUpdated(CastSession castSession);
  }
}
