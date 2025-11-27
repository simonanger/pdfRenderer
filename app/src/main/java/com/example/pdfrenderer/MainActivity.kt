package com.example.pdfrenderer

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfrenderer.databinding.ActivityMainBinding
import com.example.pdfrenderer.gestures.PinchZoomGestureListener
import com.example.pdfrenderer.viewmodel.PDFViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val pdfViewModel: PDFViewModel by viewModels()
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    @Inject
    lateinit var pinchZoomListener: PinchZoomGestureListener

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadPDF(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scaleGestureDetector = ScaleGestureDetector(this, pinchZoomListener)

        setupUIListeners()
        observeViewModel()
    }

    private fun setupUIListeners() {
        binding.apply {
            openPdfButton.setOnClickListener {
                filePicker.launch("application/pdf")
            }

            nextPageButton.setOnClickListener {
                pdfViewModel.nextPage()
            }

            previousPageButton.setOnClickListener {
                pdfViewModel.previousPage()
            }

            resetZoomButton.setOnClickListener {
                pdfViewModel.resetZoom()
                updateImageViewScale()
            }

            zoomInButton.setOnClickListener {
                val currentZoom = pdfViewModel.zoomLevel.value ?: 1f
                pdfViewModel.setZoomLevel(currentZoom + 0.5f)
                updateImageViewScale()
            }

            zoomOutButton.setOnClickListener {
                val currentZoom = pdfViewModel.zoomLevel.value ?: 1f
                pdfViewModel.setZoomLevel((currentZoom - 0.5f).coerceAtLeast(0.5f))
                updateImageViewScale()
            }

            pdfImageView.setOnTouchListener { _, event ->
                scaleGestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    private fun observeViewModel() {
        pdfViewModel.apply {
            currentPageBitmap.observe(this@MainActivity) { bitmap ->
                if (bitmap != null) {
                    binding.pdfImageView.setImageBitmap(bitmap)
                    updateImageViewScale()
                }
            }

            currentPage.observe(this@MainActivity) { pageIndex ->
                binding.pageIndicator.text = "Page ${pageIndex + 1} of ${totalPages.value ?: 0}"
            }

            totalPages.observe(this@MainActivity) { totalPages ->
                binding.pageIndicator.text = "Page ${currentPage.value?.plus(1) ?: 1} of $totalPages"
            }

            zoomLevel.observe(this@MainActivity) { zoom ->
                binding.zoomIndicator.text = "Zoom: %.1fx".format(zoom)
                updateImageViewScale()
            }

            isLoading.observe(this@MainActivity) { isLoading ->
                binding.loadingIndicator.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
            }

            error.observe(this@MainActivity) { errorMessage ->
                if (errorMessage != null) {
                    showError(errorMessage)
                    binding.errorTextView.apply {
                        text = errorMessage
                        visibility = android.view.View.VISIBLE
                    }
                } else {
                    binding.errorTextView.visibility = android.view.View.GONE
                }
            }
        }
    }

    private fun updateImageViewScale() {
        val zoom = pdfViewModel.zoomLevel.value ?: 1f
        binding.pdfImageView.scaleX = zoom
        binding.pdfImageView.scaleY = zoom
    }

    private fun loadPDF(uri: Uri) {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                pdfViewModel.openPDF(parcelFileDescriptor)
            } else {
                showError("Failed to open PDF file")
            }
        } catch (e: Exception) {
            showError("Error opening PDF: ${e.message}")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfViewModel.closePDF()
    }
}
