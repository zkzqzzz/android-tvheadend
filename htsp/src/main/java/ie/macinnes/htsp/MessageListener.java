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

import android.os.Handler;
import android.util.Log;

import java.util.HashMap;

public class MessageListener implements IMessageListener {
    private static final String TAG = MessageListener.class.getName();

    private Handler mHandler;
    private HashMap<Long, MessageHandler> mMessageResponseCallbacks = new HashMap<>();
    private HashMap<Class<? extends ResponseMessage>, MessageHandler> mMessageTypeCallbacks = new HashMap<>();

    public MessageListener() {}

    public MessageListener(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void onMessage(ResponseMessage message) {
        Log.v(TAG, "Received Message: " + message.toString());

        Long seq = message.getSeq();
        MessageHandler handler;

        if (mMessageResponseCallbacks.containsKey(seq)) {
            handler = mMessageResponseCallbacks.remove(seq);
        } else if (mMessageTypeCallbacks.containsKey(message.getClass())) {
            handler = mMessageTypeCallbacks.get(message.getClass());
        } else {
            Log.v(TAG, "Dropping message, no callbacks registered");
            return;
        }

        handler.setMessage(message);

        if (mHandler != null) {
            // User has supplied a Handler to execute callbacks on
            mHandler.post(handler);
        } else {
            // Execute in our own thread
            handler.run();
        }
    }

    public void addMessageResponseHandler(Long seq, MessageHandler runnable) {
        mMessageResponseCallbacks.put(seq, runnable);
    }

    public void addMessageTypeHandler(Class<? extends ResponseMessage> clazz, MessageHandler runnable) {
        mMessageTypeCallbacks.put(clazz, runnable);
    }
}
