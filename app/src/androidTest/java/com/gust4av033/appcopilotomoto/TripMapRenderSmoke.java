package com.gust4av033.appcopilotomoto;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.PixelCopy;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import android.graphics.PointF;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import java.io.File;
import java.io.FileOutputStream;
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
    private static boolean containsTrace(Bitmap bitmap, int color, PointF start) {
        if (bitmap == null) return false;
        int count = 0;
        for (int y = Math.max(0, (int)start.y - 5); y < Math.min(bitmap.getHeight(), (int)start.y + 6); y++) {
            for (int x = Math.max(0, (int)start.x - 5); x < Math.min(bitmap.getWidth(), (int)start.x + 6); x++) {
                int pixel = bitmap.getPixel(x, y);
                if (Math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(color)) <= 12 &&
                    Math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(color)) <= 12 &&
                    Math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(color)) <= 12 &&
                    ++count >= 5) return true;
            }
        }
        return false;
    }
    /** Uses the production local style: planned cyan line is separate from the dashed trace. */
    private static void verifyPlannedRoute(Instrumentation instrumentation, MapLibreMap map) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try {
                org.maplibre.android.style.sources.GeoJsonSource source = map.getStyle().getSourceAs("trip-planned-route");
                if (source == null) throw new IllegalStateException("Missing reserved route source");
                source.setGeoJson("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[-90.4997,14.6004],[-90.4997,14.6007]]}}");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new IllegalStateException("Route source", failure.get());
        boolean rendered = false;
        for (int attempt = 0; attempt < 10 && !rendered; attempt++) {
            CountDownLatch ready = new CountDownLatch(1);
            AtomicReference<Boolean> visible = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> map.snapshot(bitmap -> {
                PointF midpoint = map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(14.60055, -90.4997));
                visible.set(containsTrace(bitmap, android.graphics.Color.parseColor("#26D3F2"), midpoint));
                ready.countDown();
            }));
            if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Route render timed out");
            rendered = visible.get();
        }
        if (!rendered) throw new IllegalStateException("Planned route not rendered by the local style");
        instrumentation.runOnMainSync(() -> map.getStyle().<org.maplibre.android.style.sources.GeoJsonSource>getSourceAs("trip-planned-route")
            .setGeoJson("{\"type\":\"FeatureCollection\",\"features\":[]}"));
    }
    /** Capture the composited map window. A symbol hit-test alone cannot prove the bike is visible. */
    private static Bitmap displayedFrame(Instrumentation instrumentation, Activity activity,
            MapLibreMap map, int traceColor, Bitmap previous) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            CountDownLatch copied = new CountDownLatch(1);
            AtomicReference<Bitmap> result = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                try {
                    if (map.getPrefetchesTiles() || map.getPrefetchZoomDelta() != 0 ||
                            map.getStyle().getSource("openmaptiles") != null ||
                            Math.abs(map.getCameraPosition().tilt - 52) > 1)
                        throw new IllegalStateException("Unexpected camera or online source");
                    MapView view = find(activity.getWindow().getDecorView());
                    int id = activity.getResources().getIdentifier("trip_map_motorcycle_marker", "id", activity.getPackageName());
                    ImageView bike = view.findViewById(id);
                    PointF fix = map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(14.6006, -90.5));
                    PointF start = map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(14.6004, -90.5));
                    if ((previous != null && Math.abs(fix.x - view.getWidth() / 2f) < 50) ||
                        map.getStyle().getLayer("trip-motorcycle-3d") == null ||
                        bike == null || bike.isShown()) {
                        copied.countDown();
                        return;
                    }
                    int[] origin = new int[2];
                    view.getLocationInWindow(origin);
                    Rect rect = new Rect(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight());
                    Bitmap frame = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                    PixelCopy.request(activity.getWindow(), rect, frame, status -> {
                        // A substantial blue body above the ground point proves the 3D avatar rendered.
                        // The old ImageView must be hidden; the track approaches from below.
                        int bodyPixels = 0;
                        for (int dy = -85; dy < -12; dy++) for (int dx = -55; dx < 55; dx++) {
                            int x = Math.round(fix.x) + dx, y = Math.round(fix.y) + dy;
                            if (x < 0 || y < 0 || x >= frame.getWidth() || y >= frame.getHeight()) continue;
                            int pixel = frame.getPixel(x, y);
                            if (android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 65 &&
                                android.graphics.Color.blue(pixel) > android.graphics.Color.green(pixel) + 45)
                                bodyPixels++;
                        }
                        if (status == PixelCopy.SUCCESS && bodyPixels >= 100 && containsTrace(frame, traceColor, start) &&
                            (previous == null || !previous.sameAs(frame)))
                            result.set(frame);
                        else frame.recycle();
                        copied.countDown();
                    }, new Handler(Looper.getMainLooper()));
                } catch (Throwable ex) { failure.set(ex); copied.countDown(); }
            });
            if (!copied.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Map window copy timed out");
            if (failure.get() != null) throw new IllegalStateException("Map inspection failed", failure.get());
            if (result.get() != null) return result.get();
            Thread.sleep(500);
        }
        throw new IllegalStateException("Displayed window missing bike at GPS fix or recorded trace");
    }
    private static void saveFrame(Activity activity, Bitmap frame, String name) throws java.io.IOException {
        try (FileOutputStream file = new FileOutputStream(new File(activity.getFilesDir(), name))) {
            frame.compress(Bitmap.CompressFormat.PNG, 100, file);
        }
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
            // Two explicitly synthetic buildings exercise depth and extrusion with no network.
            instrumentation.runOnMainSync(() -> {
                String buildings = "{\"type\":\"FeatureCollection\",\"features\":["
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-90.50035,14.60065],[-90.50016,14.60065],[-90.50016,14.6009],[-90.50035,14.6009],[-90.50035,14.60065]]]}} ,"
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-90.4998,14.6007],[-90.4996,14.6007],[-90.4996,14.6009],[-90.4998,14.6009],[-90.4998,14.6007]]]}}]}";
                map.getStyle().addSource(new org.maplibre.android.style.sources.GeoJsonSource("test-buildings", buildings));
                map.getStyle().addLayerBelow(new org.maplibre.android.style.layers.FillExtrusionLayer("test-buildings", "test-buildings")
                    .withProperties(org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor("#4A5E78"),
                        org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight(24f),
                        org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity(1f)), "trip-recorded-route");
                // Fixture park is intentionally synthetic; production uses mapped green polygons only.
                String park = "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[-90.50065,14.60075],[-90.5004,14.60075],[-90.5004,14.60115],[-90.50065,14.60115],[-90.50065,14.60075]]]}}";
                com.gust4av033.appcopilotomoto.presentation.components.MapVegetation vegetation =
                    com.gust4av033.appcopilotomoto.presentation.components.MapVegetation.INSTANCE;
                String forest = vegetation.build(vegetation.polygons(java.util.Collections.singletonList(park)),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    new com.gust4av033.appcopilotomoto.presentation.components.MapBounds(-90.501,14.6002,-90.499,14.6013),18.0,true);
                org.maplibre.android.style.sources.GeoJsonSource trees = map.getStyle().getSourceAs("map-stylised-vegetation");
                trees.setGeoJson(forest);

            });
            int expected = target.getIntent().getIntExtra("fixture_route_color", 0);
            if (expected == 0) throw new IllegalStateException("Fixture route color unavailable");
            Bitmap bitmap = displayedFrame(instrumentation, target, map, expected, null);
            saveFrame(target, bitmap, "trip-map-native.png");
            instrumentation.runOnMainSync(() -> map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory
                .newLatLng(new org.maplibre.android.geometry.LatLng(14.60058, -90.49972))));
            Bitmap afterPan = displayedFrame(instrumentation, target, map, expected, bitmap);
            saveFrame(target, afterPan, "trip-map-panned.png");
            afterPan.recycle();
            verifyPlannedRoute(instrumentation, map);
            Bitmap screen = instrumentation.getUiAutomation().takeScreenshot();
            if (screen != null) try (FileOutputStream file = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), "trip-map-native-screen.png"))) {
                screen.compress(Bitmap.CompressFormat.PNG, 100, file);
            }
            result.putString("stream", "Native 3D motorcycle visible above the projected fix before and after moving the 52-degree camera; trace rendered; prefetch off; online source absent; GPS/audio never opened.\n");
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
