package com.example.pdfrenderer.gestures

import android.view.ScaleGestureDetector

/**
 * Interface for handling zoom changes from pinch-to-zoom gestures.
 * Implement this in your ViewModels to receive zoom level updates.
 */
interface ZoomListener {
    fun onZoomChange(zoomLevel: Float)
}

/**
 * Gesture listener for pinch-to-zoom functionality.
 * Decoupled from specific ViewModels - works with any ZoomListener implementation.
 */
class PinchZoomGestureListener(
    private val zoomListener: ZoomListener
) : ScaleGestureDetector.OnScaleGestureListener {

    private var currentZoom = 1f

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        val newZoom = (currentZoom * scaleFactor).coerceIn(0.5f, 5f)

        if (newZoom != currentZoom) {
            currentZoom = newZoom
            zoomListener.onZoomChange(newZoom)
        }

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // Scale gesture ended - no additional action needed
    }

    fun resetZoom() {
        currentZoom = 1f
        zoomListener.onZoomChange(1f)
    }
}
