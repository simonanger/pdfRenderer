package com.example.pdfrenderer.viewmodel

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfrenderer.gestures.ZoomListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PDFViewModel : ViewModel(), ZoomListener {
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    private val _totalPages = MutableLiveData(0)
    val totalPages: LiveData<Int> = _totalPages

    private val _zoomLevel = MutableLiveData(1f)
    val zoomLevel: LiveData<Float> = _zoomLevel

    private val _scrollX = MutableLiveData(0f)
    val scrollX: LiveData<Float> = _scrollX

    private val _scrollY = MutableLiveData(0f)
    val scrollY: LiveData<Float> = _scrollY

    private val _currentPageBitmap = MutableLiveData<Bitmap?>(null)
    val currentPageBitmap: LiveData<Bitmap?> = _currentPageBitmap

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val MIN_ZOOM = 0.5f
    private val MAX_ZOOM = 5f
    private val RENDER_WIDTH = 1080
    private val RENDER_HEIGHT = 1440

    fun openPDF(fileDescriptor: ParcelFileDescriptor) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _isLoading.postValue(true)
                this@PDFViewModel.fileDescriptor = fileDescriptor
                pdfRenderer = PdfRenderer(fileDescriptor)
                _totalPages.postValue(pdfRenderer?.pageCount ?: 0)
                _currentPage.postValue(0)
                _zoomLevel.postValue(1f)
                _scrollX.postValue(0f)
                _scrollY.postValue(0f)
                renderPage(0)
            } catch (e: Exception) {
                _error.postValue("Failed to open PDF: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun goToPage(pageIndex: Int) {
        val totalPages = _totalPages.value ?: 0
        if (pageIndex in 0 until totalPages) {
            _currentPage.value = pageIndex
            _scrollX.value = 0f
            _scrollY.value = 0f
            renderPage(pageIndex)
        }
    }

    fun nextPage() {
        val currentPage = _currentPage.value ?: 0
        val totalPages = _totalPages.value ?: 0
        if (currentPage < totalPages - 1) {
            goToPage(currentPage + 1)
        }
    }

    fun previousPage() {
        val currentPage = _currentPage.value ?: 0
        if (currentPage > 0) {
            goToPage(currentPage - 1)
        }
    }

    fun setZoomLevel(zoomLevel: Float) {
        val constrainedZoom = zoomLevel.coerceIn(MIN_ZOOM, MAX_ZOOM)
        _zoomLevel.value = constrainedZoom
    }

    fun updateScroll(x: Float, y: Float) {
        val maxScrollX = getMaxScrollX()
        val maxScrollY = getMaxScrollY()
        _scrollX.value = x.coerceIn(0f, maxScrollX)
        _scrollY.value = y.coerceIn(0f, maxScrollY)
    }

    fun resetZoom() {
        _zoomLevel.value = 1f
        _scrollX.value = 0f
        _scrollY.value = 0f
    }

    override fun onZoomChange(zoomLevel: Float) {
        setZoomLevel(zoomLevel)
    }

    private fun renderPage(pageIndex: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val page = pdfRenderer?.openPage(pageIndex)
                if (page != null) {
                    val bitmap = Bitmap.createBitmap(RENDER_WIDTH, RENDER_HEIGHT, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    _currentPageBitmap.postValue(bitmap)
                }
            } catch (e: Exception) {
                _error.postValue("Failed to render page: ${e.message}")
            }
        }
    }

    fun getMaxScrollX(): Float {
        val bitmap = _currentPageBitmap.value ?: return 0f
        val zoom = _zoomLevel.value ?: 1f
        return (bitmap.width * zoom - 1080).coerceAtLeast(0f)
    }

    fun getMaxScrollY(): Float {
        val bitmap = _currentPageBitmap.value ?: return 0f
        val zoom = _zoomLevel.value ?: 1f
        return (bitmap.height * zoom - 1440).coerceAtLeast(0f)
    }

    fun closePDF() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            // Silently handle close errors
        }
        pdfRenderer = null
        fileDescriptor = null
    }

    override fun onCleared() {
        super.onCleared()
        closePDF()
    }
}
