package com.myvu.client.feature;

import android.content.Context;
import android.view.View;

import com.myvu.client.service.ConnectionManager;

/**
 * Extension point for everything the glasses app can do.
 *
 * <p>To add a new capability later, implement this interface, return an optional
 * UI view from {@link #createView(Context)}, and register one instance in
 * {@code MusicActivity#onCreate}. The host takes care of lifecycle dispatch
 * (attach / connected / disconnected / detach), so a feature never has to
 * manage the Bluetooth connection itself.</p>
 */
public interface GlassesFeature {

    /** Stable id, used for logs and to find a feature by name. */
    String id();

    /** Short label shown in the host UI. */
    String label();

    /**
     * Build this feature's control view. Return {@code null} for a headless
     * feature (e.g. a background reaction to a glasses button).
     */
    View createView(Context context);

    /** Called once when the feature is attached to a host. */
    void onAttach(FeatureContext ctx);

    /** Called when the glasses relay is connected and ready to receive actions. */
    void onConnected();

    /** Called when the glasses connection drops. Stop any output here. */
    void onDisconnected();

    /** Called once when the host is torn down. Release all resources. */
    void onDetach();

    /** Services a feature may use, supplied by the host. */
    interface FeatureContext {
        Context appContext();

        /** Current connection, or null before the service is bound. */
        ConnectionManager connection();

        /** True when the app relay is connected and usable. */
        boolean isConnected();

        /** Post a runnable onto the UI thread. */
        void runOnUi(Runnable r);

        /** Append a line to the on-screen log. */
        void log(String line);
    }
}
