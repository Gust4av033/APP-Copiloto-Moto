package com.gust4av033.appcopilotomoto;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises production MapLibre/Compose lifecycle using the private field-test scenario. No HTTP/GPS/audio. */
public final class TripMapRenderSmoke {
    private static MapView find(View view) {
        if (view instanceof MapView) return (MapView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                MapView found = find(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    public static void run(Instrumentation instrumentation) {
        Bundle result = new Bundle();
        Activity activity = null;
        int resultCode = Activity.RESULT_CANCELED;
        try {
            Intent intent = new Intent().setComponent(new ComponentName(instrumentation.getTargetContext().getPackageName(),
                "com.gust4av033.appcopilotomoto.TripMapCaptureActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = instrumentation.startActivitySync(intent);
            final Activity target = activity;
            AtomicReference<MapLibreMap> readyMap = new AtomicReference<>();
            for (int attempt = 0; attempt < 120 && readyMap.get() == null; attempt++) {
                instrumentation.runOnMainSync(() -> {
                    MapView view = find(target.getWindow().getDecorView());
                    if (view != null) view.getMapAsync(map -> {
                        if (map.getStyle() != null && map.getStyle().isFullyLoaded()) readyMap.set(map);
                    });
                });
                if (readyMap.get() == null) Thread.sleep(500);
            }
            MapLibreMap map = readyMap.get();
            if (map == null) throw new IllegalStateException("Map style failed to initialize");
            AtomicReference<Bitmap> snapshot = new AtomicReference<>();
            AtomicReference<Throwable> mapFailure = new AtomicReference<>();
            CountDownLatch drawn = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                try {
                    if (map.getPrefetchZoomDelta() != 0 || map.getPrefetchesTiles())
                        throw new IllegalStateException("Prefetch must be disabled");
                    if (map.getStyle().getSource("osm-standard") != null)
                        throw new IllegalStateException("Fixture must not load online tiles");
                    map.snapshot(bitmap -> { snapshot.set(bitmap); drawn.countDown(); });
                } catch (Throwable failure) { mapFailure.set(failure); drawn.countDown(); }
            });
            if (mapFailure.get() != null) throw new IllegalStateException("Native map unavailable", mapFailure.get());
            if (!drawn.await(60, TimeUnit.SECONDS) || snapshot.get() == null)
                throw new IllegalStateException("Native snapshot unavailable");
            Bitmap bitmap = snapshot.get();
            int expected = target.getIntent().getIntExtra("fixture_route_color", 0);
            if (expected == 0) throw new IllegalStateException("Fixture route color unavailable");
            int colored = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
                int pixel = bitmap.getPixel(x,y);
                boolean matches = true;
                for (int shift : new int[]{0,8,16})
                    if (Math.abs(((pixel >> shift) & 255) - ((expected >> shift) & 255)) > 12) matches = false;
                if (matches) colored++;
            }
            if (colored < 10) throw new IllegalStateException("Recorded trace was not rendered");
            try (FileOutputStream file = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), "trip-map-native.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, file);
            }
            Bitmap screen = instrumentation.getUiAutomation().takeScreenshot();
            if (screen != null) try (FileOutputStream file = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), "trip-map-native-screen.png"))) {
                screen.compress(Bitmap.CompressFormat.PNG, 100, file);
            }
            result.putString("stream", "Native MapLibre fixture rendered; prefetch off; online source absent; GPS/audio never opened.\n");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("stream", "Map render FAILED: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        } finally {
            if (activity != null) {
                final Activity target = activity;
                instrumentation.runOnMainSync(target::finish);
            }
        }
        instrumentation.finish(resultCode, result);
    }
}
