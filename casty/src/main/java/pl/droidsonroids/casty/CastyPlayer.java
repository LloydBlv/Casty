package pl.droidsonroids.casty;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import timber.log.Timber;

public class CastyPlayer {
  private RemoteMediaClient remoteMediaClient;
  private OnMediaLoadedListener onMediaLoadedListener;

  //Needed for NoOp instance
  CastyPlayer() {
    //no-op
  }

  CastyPlayer(OnMediaLoadedListener onMediaLoadedListener) {
    this.onMediaLoadedListener = onMediaLoadedListener;
  }

  void setRemoteMediaClient(RemoteMediaClient remoteMediaClient) {
    this.remoteMediaClient = remoteMediaClient;
  }

  public void addProgressListener(final RemoteMediaClient.ProgressListener progressListener,
      final long period) {
    remoteMediaClient.addProgressListener(progressListener, period);
  }

  public void removeProgressListener(final RemoteMediaClient.ProgressListener progressListener) {
    remoteMediaClient.removeProgressListener(progressListener);
  }

  /**
   * Plays the current media file if it is paused
   */
  public void play() {
    if (isPaused()) remoteMediaClient.play();
  }

  /**
   * Pauses the current media file if it is playing
   */
  public void pause() {
    if (isPlaying()) remoteMediaClient.pause();
  }

  /**
   * Seeks the current media file
   *
   * @param time the number of milliseconds to seek by
   */
  public void seek(long time) {
    if (remoteMediaClient != null) remoteMediaClient.seek(time);
  }

  /**
   * Tries to play or pause the current media file, depending of the current state
   */
  public void togglePlayPause() {
    if (remoteMediaClient != null) {
      if (remoteMediaClient.isPlaying()) {
        remoteMediaClient.pause();
      } else if (remoteMediaClient.isPaused()) {
        remoteMediaClient.play();
      }
    }
  }

  /**
   * Checks if the media file is playing
   *
   * @return true if the media file is playing, false otherwise
   */
  public boolean isPlaying() {
    return remoteMediaClient != null && remoteMediaClient.isPlaying();
  }

  /**
   * Checks if the media file is paused
   *
   * @return true if the media file is paused, false otherwise
   */
  public boolean isPaused() {
    return remoteMediaClient != null && remoteMediaClient.isPaused();
  }

  /**
   * Checks if the media file is buffering
   *
   * @return true if the media file is buffering, false otherwise
   */
  public boolean isBuffering() {
    return remoteMediaClient != null && remoteMediaClient.isBuffering();
  }

  /**
   * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
   *
   * @param mediaData Information about the media
   * @return true if attempt was successful, false otherwise
   * @see MediaData
   */
  @MainThread public boolean loadMediaAndPlay(@NonNull MediaData mediaData) {
    return loadMediaAndPlay(mediaData.createMediaInfo(), mediaData.autoPlay, mediaData.position);
  }

  /**
   * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
   *
   * @param mediaInfo Information about the media
   * @return true if attempt was successful, false otherwise
   * @see MediaInfo
   */
  @MainThread public boolean loadMediaAndPlay(@NonNull MediaInfo mediaInfo) {
    return loadMediaAndPlay(mediaInfo, true, 0);
  }

  /**
   * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
   *
   * @param mediaInfo Information about the media
   * @param autoPlay True if the media file should start automatically
   * @param position Start position of video in milliseconds
   * @return true if attempt was successful, false otherwise
   * @see MediaInfo
   */
  @MainThread public boolean loadMediaAndPlay(@NonNull MediaInfo mediaInfo, boolean autoPlay,
      long position) {
    return playMediaBaseMethod(mediaInfo, autoPlay, position, false);
  }

  /**
   * Tries to load the media file and play in background
   *
   * @param mediaData Information about the media
   * @return true if attempt was successful, false otherwise
   * @see MediaData
   */
  @MainThread public boolean loadMediaAndPlayInBackground(@NonNull MediaData mediaData) {
    return loadMediaAndPlayInBackground(mediaData.createMediaInfo(), mediaData.autoPlay,
        mediaData.position);
  }

  /**
   * Tries to load the media file and play in background
   *
   * @param mediaInfo Information about the media
   * @return true if attempt was successful, false otherwise
   * @see MediaInfo
   */
  @MainThread public boolean loadMediaAndPlayInBackground(@NonNull MediaInfo mediaInfo) {
    return loadMediaAndPlayInBackground(mediaInfo, true, 0);
  }

  /**
   * Tries to load the media file and play in background
   *
   * @param mediaInfo Information about the media
   * @param autoPlay True if the media file should start automatically
   * @param position Start position of video in milliseconds
   * @return true if attempt was successful, false otherwise
   * @see MediaInfo
   */
  @MainThread public boolean loadMediaAndPlayInBackground(@NonNull MediaInfo mediaInfo,
      boolean autoPlay, long position) {
    return playMediaBaseMethod(mediaInfo, autoPlay, position, true);
  }

  private boolean playMediaBaseMethod(MediaInfo mediaInfo, boolean autoPlay, long position,
      boolean inBackground) {
    Timber.d(
        "playMediaBaseMethod(), url:[%s], mediaInfo:[%s], autoPlay:[%s], position:[%s] inBackground:[%s], remoteMediaClient:[%s]",
        mediaInfo.getContentId(), mediaInfo.toJson(), autoPlay, position, inBackground, remoteMediaClient);
    if (remoteMediaClient == null) {
      return false;
    }
    if (!inBackground) {
      remoteMediaClient.registerCallback(createRemoteMediaClientListener());
    }
    remoteMediaClient.load(mediaInfo,
        new MediaLoadOptions.Builder().setPlayPosition(position).setAutoplay(autoPlay).build());
    //remoteMediaClient.load(mediaInfo, autoPlay, position);
    return true;
  }

  private RemoteMediaClient.Callback createRemoteMediaClientListener() {
    return new RemoteMediaClient.Callback() {
      @Override public void onStatusUpdated() {
        Timber.d("onStatusUpdated");
        onMediaLoadedListener.onMediaLoaded();
        remoteMediaClient.unregisterCallback(this);
      }

      @Override public void onMetadataUpdated() {
        Timber.d("onMetadataUpdated");

        //no-op
      }

      @Override public void onQueueStatusUpdated() {
        Timber.d("onQueueStatusUpdated");

        //no-op
      }

      @Override public void onPreloadStatusUpdated() {
        Timber.d("onPreloadStatusUpdated");

        //no-op
      }

      @Override public void onSendingRemoteMediaRequest() {
        Timber.d("onSendingRemoteMediaRequest");

        //no-op
      }

      @Override public void onAdBreakStatusUpdated() {
        Timber.d("onAdBreakStatusUpdated");

        //no-op
      }
    };
  }
    /*
    private RemoteMediaClient.Listener createRemoteMediaClientListener() {
        return new RemoteMediaClient.Listener() {
            @Override
            public void onStatusUpdated() {
                Timber.d("onStatusUpdated");
                onMediaLoadedListener.onMediaLoaded();
                remoteMediaClient.removeListener(this);
            }

            @Override
            public void onMetadataUpdated() {
                Timber.d("onMetadataUpdated");

                //no-op
            }

            @Override
            public void onQueueStatusUpdated() {
                Timber.d("onQueueStatusUpdated");

                //no-op
            }

            @Override
            public void onPreloadStatusUpdated() {
                Timber.d("onPreloadStatusUpdated");

                //no-op
            }

            @Override
            public void onSendingRemoteMediaRequest() {
                Timber.d("onSendingRemoteMediaRequest");

                //no-op
            }

            @Override
            public void onAdBreakStatusUpdated() {
                Timber.d("onAdBreakStatusUpdated");

                //no-op
            }
        };
    }
    */

  interface OnMediaLoadedListener {
    void onMediaLoaded();
  }
}
