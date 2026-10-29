package com.codex.steeringlandscape;

import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Method;

/** Read-only access to the vehicle's cached HSCAN values. */
final class VehicleReader {
    private static final String DESCRIPTOR = "com.hkmc.telematics.hscan.tm_interface.ITM_HSCANService";
    private static final int ANGLE = 0xc2;
    private static final int PEDAL = 0xbd;
    private static final int CYLINDER_PRESSURE = 0xbe;
    private IBinder binder;

    private float read(int transaction) throws Exception {
        if (binder == null || !binder.isBinderAlive()) {
            Method getService = Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            binder = (IBinder) getService.invoke(null, "tm_hscan_service");
        }
        if (binder == null) throw new IllegalStateException("service unavailable");
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!binder.transact(transaction, data, reply, 0))
                throw new IllegalStateException("transaction rejected");
            reply.readException();
            return reply.readFloat();
        } catch (Exception e) {
            binder = null;
            throw e;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    float angle() throws Exception {
        float value = read(ANGLE);
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value > 65535)
            throw new IllegalStateException("invalid angle");
        int raw = Math.round(value);
        if (Math.abs(value - raw) > .001f) throw new IllegalStateException("invalid angle");
        return (raw < 0x8000 ? raw : raw - 0x10000) / 10f;
    }

    // Candidate ESC cylinder pressure, not a verified brake-pedal percentage.
    float brakePressure() throws Exception {
        float value = read(CYLINDER_PRESSURE);
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value >= 409.5f)
            throw new IllegalStateException("invalid pressure");
        return value;
    }

    float pedal() throws Exception {
        float value = read(PEDAL);
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0 || value > 100.01f)
            throw new IllegalStateException("invalid pedal");
        return Math.min(value, 100f);
    }
}
