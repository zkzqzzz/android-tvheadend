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

package ie.macinnes.htsp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class HtspService extends Service {
    private static final String TAG = HtspService.class.getName();

    private Connection mConnection;
    private Thread mHtspConnectionThread;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public HtspService getService() {
            // Return this instance of LocalService so clients can call public methods
            return HtspService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating HTSP Connection");
        super.onCreate();

        mConnection = new Connection();
        mHtspConnectionThread = new Thread(mConnection);
        mHtspConnectionThread.start();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Closing HTSP Connection");
        mConnection.close();
    }

    public void addMessageListener(IMessageListener messageListener) {
        mConnection.addMessageListener(messageListener);
    }

    public void addConnectionListener(IConnectionListener connectionListener) {
        mConnection.addConnectionListener(connectionListener);
    }

    public void sendMessage(RequestMessage message) {
        mConnection.sendMessage(message);
    }
}
