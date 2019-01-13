package com.kayo.lib.worker;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by shilei
 * 2018/12/14
 * <pre>
 * 线程切换主线程
 * </pre>
 */

public class WeakHandler {

    private final Handler.Callback mCallback;
    private final ExecHandler mExec;
    private Lock mLock = new ReentrantLock();
    @SuppressWarnings("ConstantConditions")
    @VisibleForTesting
    final ChainedRef mRunnables = new ChainedRef(mLock, null);

    public WeakHandler() {
        mCallback = null;
        mExec = new ExecHandler();
    }

    public WeakHandler(@Nullable Handler.Callback callback) {
        mCallback = callback;
        mExec = new ExecHandler(new WeakReference<>(callback));
    }

    public WeakHandler(@NonNull Looper looper) {
        mCallback = null;
        mExec = new ExecHandler(looper);
    }

    public WeakHandler(@NonNull Looper looper, @NonNull Handler.Callback callback) {
        mCallback = callback;
        mExec = new ExecHandler(looper, new WeakReference<>(callback));
    }

    public final boolean post(@NonNull Runnable r) {
        return mExec.post(wrapRunnable(r));
    }

    public final boolean postAtTime(@NonNull Runnable r, long uptimeMillis) {
        return mExec.postAtTime(wrapRunnable(r), uptimeMillis);
    }

    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis) {
        return mExec.postAtTime(wrapRunnable(r), token, uptimeMillis);
    }

    public final boolean postDelayed(Runnable r, long delayMillis) {
        return mExec.postDelayed(wrapRunnable(r), delayMillis);
    }

    public final boolean postAtFrontOfQueue(Runnable r) {
        return mExec.postAtFrontOfQueue(wrapRunnable(r));
    }

    public final void removeCallbacks(Runnable r) {
        final WeakRunnable runnable = mRunnables.remove(r);
        if (runnable != null) {
            mExec.removeCallbacks(runnable);
        }
    }

    public final void removeCallbacks(Runnable r, Object token) {
        final WeakRunnable runnable = mRunnables.remove(r);
        if (runnable != null) {
            mExec.removeCallbacks(runnable, token);
        }
    }

    public final boolean sendMessage(Message msg) {
        return mExec.sendMessage(msg);
    }

    public final boolean sendEmptyMessage(int what) {
        return mExec.sendEmptyMessage(what);
    }

    public final boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        return mExec.sendEmptyMessageDelayed(what, delayMillis);
    }

    public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis) {
        return mExec.sendEmptyMessageAtTime(what, uptimeMillis);
    }

    public final boolean sendMessageDelayed(Message msg, long delayMillis) {
        return mExec.sendMessageDelayed(msg, delayMillis);
    }

    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        return mExec.sendMessageAtTime(msg, uptimeMillis);
    }

    public final boolean sendMessageAtFrontOfQueue(Message msg) {
        return mExec.sendMessageAtFrontOfQueue(msg);
    }

    public final void removeMessages(int what) {
        mExec.removeMessages(what);
    }

    public final void removeMessages(int what, Object object) {
        mExec.removeMessages(what, object);
    }

    public final void removeCallbacksAndMessages(Object token) {
        mExec.removeCallbacksAndMessages(token);
    }

    public final boolean hasMessages(int what) {
        return mExec.hasMessages(what);
    }

    public final boolean hasMessages(int what, Object object) {
        return mExec.hasMessages(what, object);
    }

    public final Looper getLooper() {
        return mExec.getLooper();
    }

    public final Message obtainMessage() {
        return Message.obtain(mExec);
    }

    public final Message obtainMessage(int what) {
        return Message.obtain(mExec, what);
    }

    public final Message obtainMessage(int what, Object obj) {
        return Message.obtain(mExec, what, obj);
    }

    public final Message obtainMessage(int what, int arg1, int arg2) {
        return Message.obtain(mExec, what, arg1, arg2);
    }

    public final Message obtainMessage(int what, int arg1, int arg2, Object obj) {
        return Message.obtain(mExec, what, arg1, arg2, obj);
    }

    private WeakRunnable wrapRunnable(@NonNull Runnable r) {
        //noinspection ConstantConditions
        if (r == null) {
            throw new NullPointerException("Runnable can't be null");
        }
        final ChainedRef hardRef = new ChainedRef(mLock, r);
        mRunnables.insertAfter(hardRef);
        return hardRef.wrapper;
    }

    private static class ExecHandler extends Handler {
        private final WeakReference<Callback> mCallback;

        ExecHandler() {
            mCallback = null;
        }

        ExecHandler(WeakReference<Callback> callback) {
            mCallback = callback;
        }

        ExecHandler(Looper looper) {
            super(looper);
            mCallback = null;
        }

        ExecHandler(Looper looper, WeakReference<Callback> callback) {
            super(looper);
            mCallback = callback;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {

            if (mCallback == null) {
                return;
            }
            final Callback callback = mCallback.get();
            if (callback == null) {
                return;
            }
            callback.handleMessage(msg);
        }
    }

    static class WeakRunnable implements Runnable {
        private final WeakReference<Runnable> mDelegate;
        private final WeakReference<ChainedRef> mReference;

        WeakRunnable(WeakReference<Runnable> delegate, WeakReference<ChainedRef> reference) {
            mDelegate = delegate;
            mReference = reference;
        }

        @Override
        public void run() {
            final Runnable delegate = mDelegate.get();
            final ChainedRef reference = mReference.get();
            if (reference != null) {
                reference.remove();
            }
            if (delegate != null) {
                delegate.run();
            }
        }
    }

    static class ChainedRef {
        @Nullable
        ChainedRef next;
        @Nullable
        ChainedRef prev;
        @NonNull
        final Runnable runnable;
        @NonNull
        final WeakRunnable wrapper;

        @NonNull
        Lock lock;

        public ChainedRef(@NonNull Lock lock, @NonNull Runnable r) {
            this.runnable = r;
            this.lock = lock;
            this.wrapper = new WeakRunnable(new WeakReference<>(r), new WeakReference<>(this));
        }

        public WeakRunnable remove() {
            lock.lock();
            try {
                if (prev != null) {
                    prev.next = next;
                }
                if (next != null) {
                    next.prev = prev;
                }
                prev = null;
                next = null;
            } finally {
                lock.unlock();
            }
            return wrapper;
        }

        public void insertAfter(@NonNull ChainedRef candidate) {
            lock.lock();
            try {
                if (this.next != null) {
                    this.next.prev = candidate;
                }

                candidate.next = this.next;
                this.next = candidate;
                candidate.prev = this;
            } finally {
                lock.unlock();
            }
        }

        @Nullable
        public WeakRunnable remove(Runnable obj) {
            lock.lock();
            try {
                ChainedRef curr = this.next;
                while (curr != null) {
                    if (curr.runnable == obj) {
                        return curr.remove();
                    }
                    curr = curr.next;
                }
            } finally {
                lock.unlock();
            }
            return null;
        }
    }
}
