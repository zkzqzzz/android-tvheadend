/*
 * Copyright (c) 2016 Kiall Mac Innes <kiall@macinnes.ie>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package ie.macinnes.tvheadend.tvinput;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.tvheadend.tasks.PrepareVideoTask;

public class MediaPlayerSession extends android.media.tv.TvInputService.Session {
    private static final String TAG = MediaPlayerSession.class.getName();
    private static AtomicInteger sSessionCounter = new AtomicInteger();

    private final Context mContext;
    private final int mSessionNumber;

    private MediaPlayer mMediaPlayer;
    private Surface mSurface;

    /**
     * Creates a new Session.
     *
     * @param context The context of the application
     */
    public MediaPlayerSession(Context context) {
        super(context);
        mContext = context;
        mSessionNumber = sSessionCounter.getAndIncrement();
        Log.d(TAG, "Session created (" + mSessionNumber + ")");
    }

    @Override
    public void onRelease() {
        Log.d(TAG, "Session onRelease (" + mSessionNumber + ")");
        stopPlayback();
    }

    @Override
    public boolean onSetSurface(Surface surface) {
        Log.d(TAG, "Session onSetSurface (" + mSessionNumber + ")");

        mSurface = surface;

        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(surface);
        }

        return true;
    }

    @Override
    public void onSetStreamVolume(float volume) {
        Log.d(TAG, "Session onSetStreamVolume: " + volume + " (" + mSessionNumber + ")");
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(volume, volume);
        }
    }

    @Override
    public boolean onTune(Uri channelUri) {
        Log.d(TAG, "Session onTune: " + channelUri + " (" + mSessionNumber + ")");

        // Notify we are busy tuning
        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

        // Stop any existing playback
        stopPlayback();

        // Prepare for a new playback
        PrepareVideoTask prepareVideoTask = new PrepareVideoTask(mContext, channelUri, 30000) {
            @Override
            protected void onPostExecute(MediaPlayer mediaPlayer) {
                mMediaPlayer = mediaPlayer;

                if (mediaPlayer != null) {
                    mediaPlayer.setSurface(mSurface);
                    mediaPlayer.start();

                    notifyVideoAvailable();
                } else {
                    Log.e(TAG, "Error preparing media playback");
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                }
            }
        };

        prepareVideoTask.execute();

        return true;
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        Log.d(TAG, "Session onSetCaptionEnabled: " + enabled + " (" + mSessionNumber + ")");
    }

    /**
     * Stop media playback
     */
    private void stopPlayback() {
        Log.d(TAG, "Session stopPlayback (" + mSessionNumber + ")");
        if (mMediaPlayer != null) {
            mMediaPlayer.setSurface(null);
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }
}