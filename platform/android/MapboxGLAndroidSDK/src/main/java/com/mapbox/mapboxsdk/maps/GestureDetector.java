package com.mapbox.mapboxsdk.maps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ScaleGestureDetectorCompat;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ZoomButtonsController;

import com.almeros.android.multitouch.gesturedetectors.RotateGestureDetector;
import com.almeros.android.multitouch.gesturedetectors.ShoveGestureDetector;
import com.almeros.android.multitouch.gesturedetectors.TwoFingerGestureDetector;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerView;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.telemetry.MapboxEvent;

import java.util.Collections;
import java.util.List;

/**
 * Manages gestures events on a MapView and invoke actions on NativeMapView.
 * <p>
 * Created and maintained by {@link MapView}, events are forwared to {@link NativeMapView}.
 * Relies on gesture detection code found in almeros.android.multitouch.gesturedetectors.
 * </p>
 */
class GestureDetector {

    private MapboxMap mapboxMap;
    private MapView mapView;
    private ZoomButtonsController zoomButtonsController;
    private NativeMapView nativeMapView;
    private IconManager iconManager;
    private AnnotationManager annotationManager;
    private PointF focalPoint;
    private float screenDensity;

    private TrackballLongPressTimeOut currentTrackballLongPressTimeOut;
    private GestureDetectorCompat gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private RotateGestureDetector rotateGestureDetector;
    private ShoveGestureDetector shoveGestureDetector;

    private boolean twoTap = false;
    private boolean zoomStarted = false;
    private boolean dragStarted = false;
    private boolean quickZoom = false;
    private boolean scrollInProgress = false;

    GestureDetector(MapboxMap mapboxMap, MapView mapView, NativeMapView nativeMapView, IconManager iconManager, AnnotationManager annotationManager) {
        this.mapboxMap = mapboxMap;
        this.mapView = mapView;
        this.nativeMapView = nativeMapView;
        this.screenDensity = nativeMapView.getPixelRatio();
        this.iconManager = iconManager;
        this.annotationManager = annotationManager;

        // Touch gesture detectors
        Context context = mapView.getContext();
        gestureDetector = new GestureDetectorCompat(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(true);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());
        ScaleGestureDetectorCompat.setQuickScaleEnabled(scaleGestureDetector, true);
        rotateGestureDetector = new RotateGestureDetector(context, new RotateGestureListener());
        shoveGestureDetector = new ShoveGestureDetector(context, new ShoveGestureListener());

        zoomButtonsController = new ZoomButtonsController(mapView);
        zoomButtonsController.setZoomSpeed(MapboxConstants.ANIMATION_DURATION);
        zoomButtonsController.setOnZoomListener(new OnZoomListener());
    }

    void setFocalPoint(PointF focalPoint) {
        if (focalPoint == null) {
            // resetting focal point,
            UiSettings uiSettings = mapboxMap.getUiSettings();
            // need to validate if we need to reset focal point with user provided one
            if (uiSettings.getFocalPoint() != null) {
                focalPoint = uiSettings.getFocalPoint();
            }
        }
        this.focalPoint = focalPoint;
    }

    // Called when user touches the screen, all positions are absolute
    boolean onTouchEvent(@NonNull MotionEvent event) {
        // Check and ignore non touch or left clicks
        if ((event.getButtonState() != 0) && (event.getButtonState() != MotionEvent.BUTTON_PRIMARY)) {
            return false;
        }

        // Check two finger gestures first
        rotateGestureDetector.onTouchEvent(event);
        scaleGestureDetector.onTouchEvent(event);
        shoveGestureDetector.onTouchEvent(event);

        // Handle two finger tap
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // First pointer down
                nativeMapView.setGestureInProgress(true);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                // Second pointer down
                twoTap = event.getPointerCount() == 2
                        && mapboxMap.getUiSettings().isZoomGesturesEnabled();
                if (twoTap) {
                    // Confirmed 2nd Finger Down
                    MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_TWO_FINGER_SINGLETAP, event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_POINTER_UP:
                // Second pointer up
                break;

            case MotionEvent.ACTION_UP:
                // First pointer up
                long tapInterval = event.getEventTime() - event.getDownTime();
                boolean isTap = tapInterval <= ViewConfiguration.getTapTimeout();
                boolean inProgress = rotateGestureDetector.isInProgress()
                        || scaleGestureDetector.isInProgress()
                        || shoveGestureDetector.isInProgress();

                if (twoTap && isTap && !inProgress) {
                    if (focalPoint != null) {
                        mapView.zoom(false, focalPoint.x, focalPoint.y);
                    } else {
                        PointF focalPoint = TwoFingerGestureDetector.determineFocalPoint(event);
                        mapView.zoom(false, focalPoint.x, focalPoint.y);
                    }
                    twoTap = false;
                    return true;
                }

                // Scroll / Pan Has Stopped
                if (scrollInProgress) {
                    MapboxEvent.trackGestureDragEndEvent(mapboxMap, event.getX(), event.getY());
                    scrollInProgress = false;
                }

                twoTap = false;
                nativeMapView.setGestureInProgress(false);
                break;

            case MotionEvent.ACTION_CANCEL:
                twoTap = false;
                nativeMapView.setGestureInProgress(false);
                break;
        }

        return gestureDetector.onTouchEvent(event);
    }

    void onDetachedFromWindow() {
        // Required by ZoomButtonController (from Android SDK documentation)
        if (mapboxMap.getUiSettings().isZoomControlsEnabled()) {
            zoomButtonsController.setVisible(false);
        }
    }

    void onVisibilityChanged(int visibility) {
        // Required by ZoomButtonController (from Android SDK documentation)
        if (visibility == View.VISIBLE) {
            if (mapboxMap != null && mapboxMap.getUiSettings().isZoomControlsEnabled()) {
                zoomButtonsController.setVisible(true);
            }
        } else {
            if (mapboxMap != null && mapboxMap.getUiSettings().isZoomControlsEnabled()) {
                zoomButtonsController.setVisible(false);
            }
        }
    }

    // This class handles one finger gestures
    private class GestureListener extends android.view.GestureDetector.SimpleOnGestureListener {

        // Must always return true otherwise all events are ignored
        @Override
        @SuppressLint("ResourceType")
        public boolean onDown(MotionEvent event) {
            // Show the zoom controls
            if (mapboxMap.getUiSettings().isZoomControlsEnabled()) {
                zoomButtonsController.setVisible(true);
            }
            return true;
        }

        // Called for double taps
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                return false;
            }

            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    if (quickZoom) {
                        // insert here?
                        quickZoom = false;
                        break;
                    }

                    // Single finger double tap
                    if (focalPoint != null) {
                        // User provided focal point
                        mapView.zoom(true, focalPoint.x, focalPoint.y);
                    } else {
                        // Zoom in on gesture
                        mapView.zoom(true, e.getX(), e.getY());
                    }
                    break;
            }

            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_DOUBLETAP, e.getX(), e.getY());

            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent motionEvent) {
            // Cancel any animation
            mapboxMap.cancelTransitions();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
            List<Marker> selectedMarkers = mapboxMap.getSelectedMarkers();

            PointF tapPoint = new PointF(motionEvent.getX(), motionEvent.getY());
            float toleranceSides = 4 * screenDensity;
            float toleranceTopBottom = 10 * screenDensity;

            RectF tapRect = new RectF(tapPoint.x - iconManager.getAverageIconWidth() / 2 - toleranceSides,
                    tapPoint.y - iconManager.getAverageIconHeight() / 2 - toleranceTopBottom,
                    tapPoint.x + iconManager.getAverageIconWidth() / 2 + toleranceSides,
                    tapPoint.y + iconManager.getAverageIconHeight() / 2 + toleranceTopBottom);

            List<Marker> nearbyMarkers = annotationManager.getMarkersInRect(tapRect);
            long newSelectedMarkerId = -1;

            if (nearbyMarkers != null && nearbyMarkers.size() > 0) {
                Collections.sort(nearbyMarkers);
                for (Marker nearbyMarker : nearbyMarkers) {
                    boolean found = false;
                    for (Marker selectedMarker : selectedMarkers) {
                        if (selectedMarker.equals(nearbyMarker)) {
                            found = true;
                        }
                    }
                    if (!found) {
                        newSelectedMarkerId = nearbyMarker.getId();
                        break;
                    }
                }
            }

            if (newSelectedMarkerId >= 0) {
                List<Annotation> annotations = mapboxMap.getAnnotations();
                int count = annotations.size();
                for (int i = 0; i < count; i++) {
                    Annotation annotation = annotations.get(i);
                    if (annotation instanceof Marker) {
                        if (annotation.getId() == newSelectedMarkerId) {
                            if (selectedMarkers.isEmpty() || !selectedMarkers.contains(annotation)) {
                                if (!(annotation instanceof MarkerView)) {
                                    mapboxMap.selectMarker((Marker) annotation);
                                } else {
                                    mapboxMap.getMarkerViewManager().onClickMarkerView((MarkerView) annotation);
                                }
                            }
                            break;
                        }
                    }
                }
            } else {
                if (mapboxMap.getUiSettings().isDeselectMarkersOnTap()) {
                    // deselect any selected marker
                    mapboxMap.deselectMarkers();
                }

                // notify app of map click
                MapboxMap.OnMapClickListener listener = mapboxMap.getOnMapClickListener();
                if (listener != null) {
                    LatLng point = mapboxMap.getProjection().fromScreenLocation(tapPoint);
                    listener.onMapClick(point);
                }
            }

            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_SINGLETAP, motionEvent.getX(), motionEvent.getY());
            return true;
        }

        // Called for a long press
        @Override
        public void onLongPress(MotionEvent motionEvent) {
            MapboxMap.OnMapLongClickListener listener = mapboxMap.getOnMapLongClickListener();
            if (listener != null && !quickZoom) {
                LatLng point = mapboxMap.getProjection().fromScreenLocation(new PointF(motionEvent.getX(), motionEvent.getY()));
                listener.onMapLongClick(point);
            }
        }

        // Called for flings
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                return false;
            }

            mapboxMap.getTrackingSettings().resetTrackingModesIfRequired(true, false);

            double decelerationRate = 1;

            // Cancel any animation
            mapboxMap.cancelTransitions();

            double offsetX = velocityX * decelerationRate / 4 / screenDensity;
            double offsetY = velocityY * decelerationRate / 4 / screenDensity;

            nativeMapView.setGestureInProgress(true);
            nativeMapView.moveBy(offsetX, offsetY, (long) (decelerationRate * 1000.0f));
            nativeMapView.setGestureInProgress(false);

            MapboxMap.OnFlingListener listener = mapboxMap.getOnFlingListener();
            if (listener != null) {
                listener.onFling();
            }

            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_PAN_START, e1.getX(), e1.getY());
            return true;
        }

        // Called for drags
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!scrollInProgress) {
                scrollInProgress = true;
            }
            if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                return false;
            }

            if (dragStarted) {
                return false;
            }

            mapView.requestDisallowInterceptTouchEvent(true);

            // reset tracking if needed
            mapboxMap.getTrackingSettings().resetTrackingModesIfRequired(true, false);
            // Cancel any animation
            mapboxMap.cancelTransitions();

            // Scroll the map
            nativeMapView.moveBy(-distanceX / screenDensity, -distanceY / screenDensity);

            MapboxMap.OnScrollListener listener = mapboxMap.getOnScrollListener();
            if (listener != null) {
                listener.onScroll();
            }
            return true;
        }
    }

    // This class handles two finger gestures and double-tap drag gestures
    private class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        long beginTime = 0;
        float scaleFactor = 1.0f;

        // Called when two fingers first touch the screen
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                return false;
            }

            beginTime = detector.getEventTime();
            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_PINCH_START, detector.getFocusX(), detector.getFocusY());
            return true;
        }

        // Called when fingers leave screen
        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            beginTime = 0;
            scaleFactor = 1.0f;
            zoomStarted = false;
        }

        // Called each time a finger moves
        // Called for pinch zooms and quickzooms/quickscales
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            UiSettings uiSettings = mapboxMap.getUiSettings();
            if (!uiSettings.isZoomGesturesEnabled()) {
                return super.onScale(detector);
            }

            // If scale is large enough ignore a tap
            scaleFactor *= detector.getScaleFactor();
            if ((scaleFactor > 1.05f) || (scaleFactor < 0.95f)) {
                zoomStarted = true;
            }

            // Ignore short touches in case it is a tap
            // Also ignore small scales
            long time = detector.getEventTime();
            long interval = time - beginTime;
            if (!zoomStarted && (interval <= ViewConfiguration.getTapTimeout())) {
                return false;
            }

            if (!zoomStarted) {
                return false;
            }

            if (dragStarted) {
                return false;
            }

            // Cancel any animation
            mapboxMap.cancelTransitions();

            // Gesture is a quickzoom if there aren't two fingers
            quickZoom = !twoTap;

            // make an assumption here; if the zoom center is specified by the gesture, it's NOT going
            // to be in the center of the map. Therefore the zoom will translate the map center, so tracking
            // should be disabled.

            mapboxMap.getTrackingSettings().resetTrackingModesIfRequired(!quickZoom, false);
            // Scale the map
            if (focalPoint != null) {
                // arround user provided focal point
                nativeMapView.scaleBy(detector.getScaleFactor(), focalPoint.x / screenDensity, focalPoint.y / screenDensity);
            } else if (quickZoom) {
                // around center map
                nativeMapView.scaleBy(detector.getScaleFactor(), (mapView.getWidth() / 2) / screenDensity, (mapView.getHeight() / 2) / screenDensity);
            } else {
                // around gesture
                nativeMapView.scaleBy(detector.getScaleFactor(), detector.getFocusX() / screenDensity, detector.getFocusY() / screenDensity);
            }

            return true;
        }
    }

    // This class handles two finger rotate gestures
    private class RotateGestureListener extends RotateGestureDetector.SimpleOnRotateGestureListener {

        long beginTime = 0;
        float totalAngle = 0.0f;
        boolean started = false;

        // Called when two fingers first touch the screen
        @Override
        public boolean onRotateBegin(RotateGestureDetector detector) {
            if (!mapboxMap.getTrackingSettings().isRotateGestureCurrentlyEnabled()) {
                return false;
            }

            beginTime = detector.getEventTime();
            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_ROTATION_START, detector.getFocusX(), detector.getFocusY());
            return true;
        }

        // Called when the fingers leave the screen
        @Override
        public void onRotateEnd(RotateGestureDetector detector) {
            beginTime = 0;
            totalAngle = 0.0f;
            started = false;
        }

        // Called each time one of the two fingers moves
        // Called for rotation
        @Override
        public boolean onRotate(RotateGestureDetector detector) {
            if (!mapboxMap.getTrackingSettings().isRotateGestureCurrentlyEnabled() || dragStarted) {
                return false;
            }

            // If rotate is large enough ignore a tap
            // Also is zoom already started, don't rotate
            totalAngle += detector.getRotationDegreesDelta();
            if (!zoomStarted && ((totalAngle > 20.0f) || (totalAngle < -20.0f))) {
                started = true;
            }

            // Ignore short touches in case it is a tap
            // Also ignore small rotate
            long time = detector.getEventTime();
            long interval = time - beginTime;
            if (!started && (interval <= ViewConfiguration.getTapTimeout())) {
                return false;
            }

            if (!started) {
                return false;
            }

            // Cancel any animation
            mapboxMap.cancelTransitions();

            // rotation constitutes translation of anything except the center of
            // rotation, so cancel both location and bearing tracking if required

            mapboxMap.getTrackingSettings().resetTrackingModesIfRequired(true, true);

            // Get rotate value
            double bearing = nativeMapView.getBearing();
            bearing += detector.getRotationDegreesDelta();

            // Rotate the map
            if (focalPoint != null) {
                // User provided focal point
                mapView.setBearing(bearing, focalPoint.x / screenDensity, focalPoint.y / screenDensity);
            } else {
                // around gesture
                mapView.setBearing(bearing, detector.getFocusX() / screenDensity, detector.getFocusY() / screenDensity);
            }
            return true;
        }
    }

    // This class handles a vertical two-finger shove. (If you place two fingers on screen with
    // less than a 20 degree angle between them, this will detect movement on the Y-axis.)
    private class ShoveGestureListener implements ShoveGestureDetector.OnShoveGestureListener {

        long beginTime = 0;
        float totalDelta = 0.0f;
        boolean started = false;

        @Override
        public boolean onShoveBegin(ShoveGestureDetector detector) {
            if (!mapboxMap.getUiSettings().isTiltGesturesEnabled()) {
                return false;
            }

            beginTime = detector.getEventTime();
            MapboxEvent.trackGestureEvent(mapboxMap, MapboxEvent.GESTURE_PITCH_START, detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public void onShoveEnd(ShoveGestureDetector detector) {
            beginTime = 0;
            totalDelta = 0.0f;
            started = false;
            dragStarted = false;
        }

        @Override
        public boolean onShove(ShoveGestureDetector detector) {
            if (!mapboxMap.getUiSettings().isTiltGesturesEnabled()) {
                return false;
            }

            // If tilt is large enough ignore a tap
            // Also if zoom already started, don't tilt
            totalDelta += detector.getShovePixelsDelta();
            if (!zoomStarted && ((totalDelta > 10.0f) || (totalDelta < -10.0f))) {
                started = true;
            }

            // Ignore short touches in case it is a tap
            // Also ignore small tilt
            long time = detector.getEventTime();
            long interval = time - beginTime;
            if (!started && (interval <= ViewConfiguration.getTapTimeout())) {
                return false;
            }

            if (!started) {
                return false;
            }

            // Cancel any animation
            mapboxMap.cancelTransitions();

            // Get tilt value (scale and clamp)
            double pitch = nativeMapView.getPitch();
            pitch -= 0.1 * detector.getShovePixelsDelta();
            pitch = Math.max(MapboxConstants.MINIMUM_TILT, Math.min(MapboxConstants.MAXIMUM_TILT, pitch));

            // Tilt the map
            mapboxMap.setTilt(pitch);

            dragStarted = true;

            return true;
        }
    }

    // This class handles input events from the zoom control buttons
    // Zoom controls allow single touch only devices to zoom in and out
    private class OnZoomListener implements ZoomButtonsController.OnZoomListener {

        // Not used
        @Override
        public void onVisibilityChanged(boolean visible) {
            // Ignore
        }

        // Called when user pushes a zoom button
        @Override
        public void onZoom(boolean zoomIn) {
            if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                return;
            }
            mapView.zoom(zoomIn);
        }
    }

    // Called when the user presses a key, also called for repeating keys held
    // down
    boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        // If the user has held the scroll key down for a while then accelerate
        // the scroll speed
        double scrollDist = event.getRepeatCount() >= 5 ? 50.0 : 10.0;

        // Check which key was pressed via hardware/real key code
        switch (keyCode) {
            // Tell the system to track these keys for long presses on
            // onKeyLongPress is fired
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                event.startTracking();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                    return false;
                }

                // Cancel any animation
                mapboxMap.cancelTransitions();

                // Move left
                nativeMapView.moveBy(scrollDist / screenDensity, 0.0 / screenDensity);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                    return false;
                }

                // Cancel any animation
                mapboxMap.cancelTransitions();

                // Move right
                nativeMapView.moveBy(-scrollDist / screenDensity, 0.0 / screenDensity);
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                    return false;
                }

                // Cancel any animation
                mapboxMap.cancelTransitions();

                // Move up
                nativeMapView.moveBy(0.0 / screenDensity, scrollDist / screenDensity);
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                    return false;
                }

                // Cancel any animation
                mapboxMap.cancelTransitions();

                // Move down
                nativeMapView.moveBy(0.0 / screenDensity, -scrollDist / screenDensity);
                return true;

            default:
                // We are not interested in this key
                return false;
        }
    }

    // Called when the user long presses a key that is being tracked
   boolean onKeyLongPress(int keyCode, KeyEvent event) {
        // Check which key was pressed via hardware/real key code
        switch (keyCode) {
            // Tell the system to track these keys for long presses on
            // onKeyLongPress is fired
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                    return false;
                }

                // Zoom out
                mapView.zoom(false);
                return true;

            default:
                // We are not interested in this key
                return false;
        }
    }

    // Called when the user releases a key
    boolean onKeyUp(int keyCode, KeyEvent event) {
        // Check if the key action was canceled (used for virtual keyboards)
        if (event.isCanceled()) {
            return false;
        }

        // Check which key was pressed via hardware/real key code
        // Note if keyboard does not have physical key (ie primary non-shifted
        // key) then it will not appear here
        // Must use the key character map as physical to character is not
        // fixed/guaranteed
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                    return false;
                }

                // Zoom in
                mapView.zoom(true);
                return true;
        }

        // We are not interested in this key
        return false;
    }

    // Called for trackball events, all motions are relative in device specific units
    boolean onTrackballEvent(MotionEvent event) {
        // Choose the action
        switch (event.getActionMasked()) {
            // The trackball was rotated
            case MotionEvent.ACTION_MOVE:
                if (!mapboxMap.getTrackingSettings().isScrollGestureCurrentlyEnabled()) {
                    return false;
                }

                // Cancel any animation
                mapboxMap.cancelTransitions();

                // Scroll the map
                nativeMapView.moveBy(-10.0 * event.getX() / screenDensity, -10.0 * event.getY() / screenDensity);
                return true;

            // Trackball was pushed in so start tracking and tell system we are
            // interested
            // We will then get the up action
            case MotionEvent.ACTION_DOWN:
                // Set up a delayed callback to check if trackball is still
                // After waiting the system long press time out
                if (currentTrackballLongPressTimeOut != null) {
                    currentTrackballLongPressTimeOut.cancel();
                    currentTrackballLongPressTimeOut = null;
                }
                currentTrackballLongPressTimeOut = new TrackballLongPressTimeOut();
                mapView.postDelayed(currentTrackballLongPressTimeOut,
                        ViewConfiguration.getLongPressTimeout());
                return true;

            // Trackball was released
            case MotionEvent.ACTION_UP:
                if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                    return false;
                }

                // Only handle if we have not already long pressed
                if (currentTrackballLongPressTimeOut != null) {
                    // Zoom in
                    mapView.zoom(true);
                }
                return true;

            // Trackball was cancelled
            case MotionEvent.ACTION_CANCEL:
                if (currentTrackballLongPressTimeOut != null) {
                    currentTrackballLongPressTimeOut.cancel();
                    currentTrackballLongPressTimeOut = null;
                }
                return true;

            default:
                // We are not interested in this event
                return false;
        }
    }

    // This class implements the trackball long press time out callback
    private class TrackballLongPressTimeOut implements Runnable {

        // Track if we have been cancelled
        private boolean cancelled;

        TrackballLongPressTimeOut() {
            cancelled = false;
        }

        // Cancel the timeout
        public void cancel() {
            cancelled = true;
        }

        // Called when long press time out expires
        @Override
        public void run() {
            // Check if the trackball is still pressed
            if (!cancelled) {
                // Zoom out
                mapView.zoom(false);

                // Ensure the up action is not run
                currentTrackballLongPressTimeOut = null;
            }
        }
    }

    // Called for events that don't fit the other handlers
    // such as mouse scroll events, mouse moves, joystick, trackpad
    boolean onGenericMotionEvent(MotionEvent event) {
        // Mouse events
        //if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) { // this is not available before API 18
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) == InputDevice.SOURCE_CLASS_POINTER) {
            // Choose the action
            switch (event.getActionMasked()) {
                // Mouse scrolls
                case MotionEvent.ACTION_SCROLL:
                    if (!mapboxMap.getUiSettings().isZoomGesturesEnabled()) {
                        return false;
                    }

                    // Cancel any animation
                    mapboxMap.cancelTransitions();

                    // Get the vertical scroll amount, one click = 1
                    float scrollDist = event.getAxisValue(MotionEvent.AXIS_VSCROLL);

                    // Scale the map by the appropriate power of two factor
                    nativeMapView.scaleBy(Math.pow(2.0, scrollDist), event.getX() / screenDensity, event.getY() / screenDensity);

                    return true;

                default:
                    // We are not interested in this event
                    return false;
            }
        }

        // We are not interested in this event
        return false;
    }

    // Called when the mouse pointer enters or exits the view
    // or when it fades in or out due to movement
    boolean onHoverEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                // Show the zoom controls
                if (mapboxMap.getUiSettings().isZoomControlsEnabled()) {
                    zoomButtonsController.setVisible(true);
                }
                return true;

            case MotionEvent.ACTION_HOVER_EXIT:
                // Hide the zoom controls
                if (mapboxMap.getUiSettings().isZoomControlsEnabled()) {
                    zoomButtonsController.setVisible(false);
                }
                return true;

            default:
                // We are not interested in this event
                return false;
        }
    }
}