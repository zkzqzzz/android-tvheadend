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
package ie.macinnes.tvheadend.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ie.macinnes.htsp.Connection;
import ie.macinnes.htsp.ConnectionListener;
import ie.macinnes.htsp.MessageHandler;
import ie.macinnes.htsp.MessageListener;
import ie.macinnes.htsp.ResponseMessage;
import ie.macinnes.htsp.messages.ChannelAddResponse;
import ie.macinnes.htsp.messages.EnableAsyncMetadataRequest;
import ie.macinnes.htsp.messages.EnableAsyncMetadataResponse;
import ie.macinnes.htsp.messages.EventAddResponse;
import ie.macinnes.htsp.messages.HelloRequest;
import ie.macinnes.htsp.messages.HelloResponse;
import ie.macinnes.htsp.messages.InitialSyncCompletedResponse;
import ie.macinnes.tvheadend.Constants;
import ie.macinnes.tvheadend.TvContractUtils;
import ie.macinnes.tvheadend.model.Channel;
import ie.macinnes.tvheadend.model.ChannelList;
import ie.macinnes.tvheadend.model.Program;
import ie.macinnes.tvheadend.model.ProgramList;


public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getName();

    public final static int STATE_IDLE = 0;
    public final static int STATE_SYNCING = 2;
    public final static int STATE_CANCELLING = 3;
    public final static int STATE_CANCELLED = 4;
    public final static int STATE_COMPLETE = 5;

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private Connection mHtspConnection;
    private Thread mHtspConnectionThread;

    private final Object mSyncLock = new Object();

    private int mState = STATE_IDLE;

    private Handler mHandler = new Handler();
    private MessageListener mMessageListener;
    private ConnectionListener mConnectionListener;

    private Account mAccount;
    private ChannelList mChannelList;
    private ProgramList mProgramList;

    private ArrayList<AsyncTask> mPendingTasks = new ArrayList<AsyncTask>();

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int INITIAL_POOL_SIZE = 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2;
    private static final int KEEP_ALIVE_TIME = 1;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "EpgSyncTask #" + mCount.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>();

    private static final Executor sExecutor
            = new ThreadPoolExecutor(INITIAL_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);

        mContext = context;
        mContentResolver = context.getContentResolver();

        initListeners();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);

        mContext = context;
        mContentResolver = context.getContentResolver();

        initListeners();
    }

    protected void initListeners() {
        mConnectionListener = new ConnectionListener(mHandler) {
            @Override
            public void onStateChange(int state, int previous) {
                Log.d(TAG, "Connected State Changed from " + previous + " to " + state);


                if (state == Connection.STATE_CONNECTED) {
                    HelloRequest helloRequest = new HelloRequest();

                    helloRequest.setUsername("kiall");
                    helloRequest.setHtspVersion(23);
                    helloRequest.setClientName("Test");
                    helloRequest.setClientVersion("1.0.0");

                    mHtspConnection.sendMessage(helloRequest);
                }
            }
        };

        mMessageListener = new MessageListener(mHandler);

        mMessageListener.addMessageTypeHandler(HelloResponse.class, new HelloHandler());
        mMessageListener.addMessageTypeHandler(EnableAsyncMetadataResponse.class, new EnableAsyncMetadataHandler());
        mMessageListener.addMessageTypeHandler(InitialSyncCompletedResponse.class, new InitialSyncCompletedHandler());
        mMessageListener.addMessageTypeHandler(ChannelAddResponse.class, new ChannelAddMessageHandler());
        mMessageListener.addMessageTypeHandler(EventAddResponse.class, new EventAddMessageHandler());
    }

    protected class HelloHandler extends MessageHandler {
        @Override
        public void onMessage(ResponseMessage message) {
            // TODO: Add Auth Step
            EnableAsyncMetadataRequest enableAsyncMetadataRequest = new EnableAsyncMetadataRequest();

            enableAsyncMetadataRequest.setEpg(1L);
            enableAsyncMetadataRequest.setEpgMaxTime((System.currentTimeMillis() / 1000L) + 60);

            mHtspConnection.sendMessage(enableAsyncMetadataRequest);
        }
    }

    protected class EnableAsyncMetadataHandler extends MessageHandler {
        @Override
        public void onMessage(ResponseMessage message) {
            Log.w(TAG, "Async Metadata Enabled");
        }
    }

    protected class InitialSyncCompletedHandler extends MessageHandler {
        @Override
        public void onMessage(ResponseMessage message) {
            Log.w(TAG, "Initial Sync Complete");

            syncChannels();

            synchronized (mSyncLock) {
                mSyncLock.notifyAll();
            }
        }
    }

    protected class ChannelAddMessageHandler extends MessageHandler {
        @Override
        public void onMessage(ResponseMessage message) {
            ChannelAddResponse channelAddResponse = (ChannelAddResponse) message;
            Log.w(TAG, "Channel Add: " + channelAddResponse.toString());
            mChannelList.add(Channel.fromHtspChannel(channelAddResponse, mAccount));
        }
    }

    protected class EventAddMessageHandler extends MessageHandler {
        @Override
        public void onMessage(ResponseMessage message) {
            EventAddResponse eventAddResponse = (EventAddResponse) message;
            Log.w(TAG, "Event Add: " + eventAddResponse.toString());
            mProgramList.add(Program.fromHtspEvent(eventAddResponse, 1, mAccount));
        }
    }

    protected void connect() {
        Log.d(TAG, "Creating HTSP Connection");

        mHtspConnection = new Connection();
        mHtspConnection.addMessageListener(mMessageListener);
        mHtspConnection.addConnectionListener(mConnectionListener);

        mHtspConnectionThread = new Thread(mHtspConnection);
        mHtspConnectionThread.start();
    }

    protected void disconnect() {
        Log.d(TAG, "Closing HTSP Connection");
        mHtspConnection.close();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        if (mState != STATE_IDLE) {
            Log.w(TAG, "Rejecting sync request, not idle");
            return;
        }

        connect();

        mAccount = account;
        mChannelList = new ChannelList();
        mProgramList = new ProgramList();

        Log.d(TAG, "Starting sync for account: " + account.toString());
        mState = STATE_SYNCING;

        final boolean quickSync = extras.getBoolean(Constants.SYNC_EXTRAS_QUICK, false);

        synchronized (mSyncLock) {
            try {
                mSyncLock.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Sync lock InterruptedException!", e);
                return;
            }
        }

        if (isCancelled()) {
            Log.i(TAG, "Sync cancelled");
            return;
        }

        // Wait for all tasks to finish
        Log.d(TAG, "Completed sync for account: " + account.toString());
        mState = STATE_COMPLETE;

        disconnect();

        mState = STATE_IDLE;
    }

    public boolean isCancelled() {
        return mState == STATE_CANCELLING || mState == STATE_CANCELLED;
    }

    @Override
    public void onSyncCanceled() {
        Log.d(TAG, "Sync cancellation requested");
        mState = STATE_CANCELLING;

        synchronized (mSyncLock) {
            mSyncLock.notifyAll();
        }

        Log.d(TAG, "Cancelling " + mPendingTasks.size() + " pending tasks");

        while (mPendingTasks.size() != 0) {
            AsyncTask asyncTask = mPendingTasks.get(0);
            asyncTask.cancel(true);
            mPendingTasks.remove(asyncTask);
        }

        mState = STATE_CANCELLED;
        disconnect();
    }

    protected boolean syncChannels() {
        // Sort the list of Channels
        Collections.sort(mChannelList);

        // Build a channel map, mapping from Original Network ID -> RowID's
        SparseArray<Long> channelMap = TvContractUtils.buildChannelMap(mContext, mChannelList);

        // Update the Channels DB - If a channel exists, update it. If not, insert a new one.
        ContentValues values;
        Long rowId;
        Uri channelUri;

        for (Channel channel : mChannelList) {
            if (isCancelled()) {
                Log.d(TAG, "Sync cancelled");
                return false;
            }

            values = channel.toContentValues();

            rowId = channelMap.get(channel.getOriginalNetworkId());

            if (rowId == null) {
                Log.d(TAG, "Adding channel: " + channel.toString());
                channelUri = mContentResolver.insert(TvContract.Channels.CONTENT_URI, values);
            } else {
                Log.d(TAG, "Updating channel: " + channel.toString());
                channelUri = TvContract.buildChannelUri(rowId);
                mContentResolver.update(channelUri, values, null, null);
                channelMap.remove(channel.getOriginalNetworkId());
            }
        }

        // Update the Channels DB - Delete channels which no longer exist.
        int size = channelMap.size();
        for (int i = 0; i < size; ++i) {
            if (isCancelled()) {
                Log.d(TAG, "Sync cancelled");
                return false;
            }

            rowId = channelMap.valueAt(i);
            Log.d(TAG, "Deleting channel: " + rowId);
            mContentResolver.delete(TvContract.buildChannelUri(rowId), null, null);
        }

        Log.d(TAG, "Completed channel sync");

        return true;
    }
}
