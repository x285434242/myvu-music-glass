package com.myvu.client.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the registered {@link GlassesFeature}s and fans lifecycle out to them. */
public final class FeatureRegistry {
    private final List<GlassesFeature> features = new ArrayList<>();
    private GlassesFeature.FeatureContext ctx;
    private boolean connected;

    public void add(GlassesFeature feature) {
        features.add(feature);
        if (ctx != null) {
            feature.onAttach(ctx);
            if (connected) feature.onConnected();
        }
    }

    public List<GlassesFeature> all() {
        return Collections.unmodifiableList(features);
    }

    public <T extends GlassesFeature> T find(Class<T> type) {
        for (GlassesFeature f : features) {
            if (type.isInstance(f)) return type.cast(f);
        }
        return null;
    }

    public void attachAll(GlassesFeature.FeatureContext ctx) {
        this.ctx = ctx;
        for (GlassesFeature f : features) f.onAttach(ctx);
    }

    public void setConnected(boolean connected) {
        if (this.connected == connected) return;
        this.connected = connected;
        for (GlassesFeature f : features) {
            if (connected) f.onConnected(); else f.onDisconnected();
        }
    }

    public void detachAll() {
        for (GlassesFeature f : features) f.onDetach();
        ctx = null;
        connected = false;
    }
}
