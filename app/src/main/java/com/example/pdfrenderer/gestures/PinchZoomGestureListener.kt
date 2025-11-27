package com.example.pdfrenderer.gestures

import android.view.ScaleGestureDetector
import com.example.pdfrenderer.viewmodel.PDFViewModel
import javax.inject.Inject

class PinchZoomGestureListener @Inject constructor(
    private val pdfViewModel: PDFViewModel
) : ScaleGestureDetector.OnScaleGestureListener {

    private var lastScaleX = 0f
    private var lastScaleY = 0f

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val scaleFactor = detector.scaleFactor
        val currentZoom = pdfViewModel.zoomLevel.value ?: 1f
        val newZoom = (currentZoom * scaleFactor).coerceIn(0.5f, 5f)

        if (newZoom != currentZoom) {
            pdfViewModel.setZoomLevel(newZoom)
        }

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        lastScaleX = detector.focusX
        lastScaleY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // Scale gesture ended - no additional action needed
    }
}
