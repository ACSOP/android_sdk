/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ddmuilib.logcat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Container for a list of log messages. The list of messages are
 * maintained in a circular buffer (FIFO).
 */
public final class LogCatMessageList {
    /** Preference key for size of the FIFO. */
    public static final String MAX_MESSAGES_PREFKEY =
            "logcat.messagelist.max.size";

    /** Default value for max # of messages. */
    public static final int MAX_MESSAGES_DEFAULT = 5000;

    private int mFifoSize;
    private BlockingQueue<LogCatMessage> mQ;
    private LogCatMessage[] mQArray;

    /**
     * Construct an empty message list.
     * @param maxMessages capacity of the circular buffer
     */
    public LogCatMessageList(int maxMessages) {
        mFifoSize = maxMessages;

        mQ = new ArrayBlockingQueue<LogCatMessage>(mFifoSize);
        mQArray = new LogCatMessage[mFifoSize];
    }

    /**
     * Resize the message list.
     * @param n new size for the list
     */
    public synchronized void resize(int n) {
        mFifoSize = n;

        if (mFifoSize > mQ.size()) {
            /* if resizing to a bigger fifo, we can copy over all elements from the current mQ */
            mQ = new ArrayBlockingQueue<LogCatMessage>(mFifoSize, true, mQ);
        } else {
            /* for a smaller fifo, copy over the last n entries */
            LogCatMessage[] curMessages = mQ.toArray(new LogCatMessage[mQ.size()]);
            mQ = new ArrayBlockingQueue<LogCatMessage>(mFifoSize);
            for (int i = curMessages.length - mFifoSize; i < curMessages.length; i++) {
                mQ.offer(curMessages[i]);
            }
        }

        mQArray = new LogCatMessage[mFifoSize];
    }

    /**
     * Append a message to the list. If the list is full, the first
     * message will be popped off of it.
     * @param m log to be inserted
     */
    public synchronized void appendMessage(final LogCatMessage m) {
        if (mQ.remainingCapacity() == 0) {
            /* make space by removing the first entry */
            mQ.poll();
        }
        mQ.offer(m);
    }

    /**
     * Clear all messages in the list.
     */
    public synchronized void clear() {
        mQ.clear();
    }

    /**
     * Obtain all the messages currently present in the list.
     * @return array containing all the log messages
     */
    public Object[] toArray() {
        if (mQ.size() == mFifoSize) {
            /*
             * Once the queue is full, it stays full until the user explicitly clears
             * all the logs. Optimize for this case by not reallocating the array.
             */
            return mQ.toArray(mQArray);
        }
        return mQ.toArray();
    }
}
