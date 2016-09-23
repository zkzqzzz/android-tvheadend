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
package ie.macinnes.tvheadend;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import ie.macinnes.htsp.IConnectionListener;
import ie.macinnes.htsp.MessageHandler;
import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.messages.ChannelAddResponse;
import ie.macinnes.htsp.messages.EnableAsyncMetadataRequest;
import ie.macinnes.htsp.messages.HelloRequest;
import ie.macinnes.htsp.messages.SubscribeRequest;
import ie.macinnes.htsp.messages.UnsubscribeRequest;
import ie.macinnes.htsp.HtspService;

public class HtspTestActivity extends Activity {
    private static final String TAG = HtspTestActivity.class.getName();
    private static final String NEWLINE = System.getProperty("line.separator");

    private HtspService mHtspService;
    boolean mHtspServiceBound = false;

    private Handler mMainHandler = new Handler();
    private MessageListener mMessageListener = new MessageListener(mMainHandler);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_htsp_test);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (mHtspServiceBound) {
            unbindService(mConnection);
            mHtspServiceBound = false;
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            HtspService.LocalBinder binder = (HtspService.LocalBinder) service;
            mHtspService = binder.getService();
//            mHtspService.addConnectionListener(mHTSPConnectionListener);
            mHtspService.addMessageListener(mMessageListener);
            mHtspServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mHtspServiceBound = false;
        }
    };

    private void setConnectionState(String state) {
        TextView v = (TextView) findViewById(R.id.statusOutput);
        v.setText(state);
    }

    private void clearDebugOutput() {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.setText(null);
    }

    private void setDebugOutput(String string) {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.setText(string);
    }

    private void appendDebugOutput(String string) {
        TextView v = (TextView) findViewById(R.id.debugOutput);
        v.append(string + NEWLINE);
    }


    private IConnectionListener mHTSPConnectionListener = new IConnectionListener() {
        @Override
        public void onStateChange(final int state, int previous) {
            Log.w(TAG, "NEW: " + Integer.toString(state) + " OLD: " + Integer.toString(previous));

            setConnectionState(Integer.toString(state));
        }
    };

    public void hello(View view) {
        if (mHtspServiceBound) {

            Log.d(TAG, "Prepping helloRequest");
            HelloRequest helloRequest = new HelloRequest();

            helloRequest.setUsername("kiall");
            helloRequest.setHtspVersion(23);
            helloRequest.setClientName("Test");
            helloRequest.setClientVersion("1.0.0");

//            mMessageListener.addMessageResponseHandler(helloRequest.getSeq(), new MessageHandler() {
//                @Override
//                public void run() {
//                    setDebugOutput(mMessage.toString());
//                }
//            });

            Log.d(TAG, "Sending helloRequest");
            mHtspService.sendMessage(helloRequest);
        }
    }

    public void enableAsyncMetadata(View view) {
        if (mHtspServiceBound) {
//            mMessageListener.addMessageTypeHandler(ChannelAddResponse.class, new MessageHandler() {
//                @Override
//                public void run() {
//                    appendDebugOutput(mMessage.toString());
//                }
//            });

            EnableAsyncMetadataRequest enableAsyncMetadataRequest = new EnableAsyncMetadataRequest();

            enableAsyncMetadataRequest.setEpg(1L);
            enableAsyncMetadataRequest.setEpgMaxTime((System.currentTimeMillis() / 1000L) + 60);

            clearDebugOutput();

            mHtspService.sendMessage(enableAsyncMetadataRequest);
        }
    }

    public void subscribe(View view) {
        if (mHtspServiceBound) {
            SubscribeRequest subscribeRequest = new SubscribeRequest();

            subscribeRequest.setChannelId(4692042L);
            subscribeRequest.setSubscriptionId(100L);

            clearDebugOutput();

            mHtspService.sendMessage(subscribeRequest);
        }
    }

    public void unsubscribe(View view) {
        if (mHtspServiceBound) {
            UnsubscribeRequest unsubscribeRequest = new UnsubscribeRequest();

            unsubscribeRequest.setSubscriptionId(100L);

            clearDebugOutput();

            mHtspService.sendMessage(unsubscribeRequest);
        }
    }

    public void startService(View view) {
        // Bind to LocalService
        Intent intent = new Intent(this, HtspService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopService(View view) {
        unbindService(mConnection);
        stopService(new Intent(this, HtspService.class));
    }
}
