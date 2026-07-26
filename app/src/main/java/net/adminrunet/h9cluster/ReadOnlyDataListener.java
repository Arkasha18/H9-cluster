package net.adminrunet.h9cluster;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/** Receives read-only value changes from the factory GWM adapter service. */
final class ReadOnlyDataListener extends Binder implements IInterface {
    interface Callback {
        void onDataChanged(String id, String value);
    }

    private static final String DESCRIPTOR =
            "com.gwm.android.adapter.IDataChangedListener";

    private final Handler mainHandler;
    private final Callback callback;

    ReadOnlyDataListener(Handler mainHandler, Callback callback) {
        this.mainHandler = mainHandler;
        this.callback = callback;
        attachInterface(this, DESCRIPTOR);
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            if (reply != null) {
                reply.writeString(DESCRIPTOR);
            }
            return true;
        }
        if (code != 1) {
            return super.onTransact(code, data, reply, flags);
        }

        data.enforceInterface(DESCRIPTOR);
        final String id = data.readString();
        final String value = data.readString();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onDataChanged(id, value);
            }
        });
        return true;
    }
}
