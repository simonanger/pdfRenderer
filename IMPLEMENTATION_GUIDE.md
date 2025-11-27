# PDF Renderer with Pinch-to-Zoom Implementation Guide

This guide explains how to integrate pinch-to-zoom functionality into your existing Android app using PdfRenderer. The implementation uses MVVM architecture with Activity, ViewModel, XML layouts, and Hilt dependency injection.

---

## Overview

The pinch-to-zoom feature is implemented through:
- **ZoomListener Interface** - Callback for zoom changes
- **PinchZoomGestureListener** - Handles pinch gestures
- **ViewModel** - Manages zoom state via LiveData
- **Activity** - Binds UI and gestures
- **XML Layout** - Displays PDF using ImageView

---

## Step 1: Create the ZoomListener Interface

**File:** `gestures/ZoomListener.kt`

```kotlin
package com.example.yourapp.gestures

interface ZoomListener {
    fun onZoomChange(zoomLevel: Float)
}
```

This interface decouples the gesture listener from specific ViewModels, allowing it to work with multiple ViewModels.

---

## Step 2: Create the PinchZoomGestureListener

**File:** `gestures/PinchZoomGestureListener.kt`

```kotlin
package com.example.yourapp.gestures

import android.view.ScaleGestureDetector

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

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    fun resetZoom() {
        currentZoom = 1f
        zoomListener.onZoomChange(1f)
    }
}
```

**Key Points:**
- Takes a `ZoomListener` in the constructor (dependency injection)
- Manages internal zoom state
- Calls `zoomListener.onZoomChange()` when zoom changes
- Constrains zoom between 0.5x and 5x

---

## Step 3: Update Your ViewModel

**File:** `viewmodel/YourPDFViewModel.kt`

```kotlin
package com.example.yourapp.viewmodel

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.yourapp.gestures.ZoomListener

class YourPDFViewModel : ViewModel(), ZoomListener {

    // Existing LiveData
    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage

    private val _totalPages = MutableLiveData(0)
    val totalPages: LiveData<Int> = _totalPages

    // New: Zoom state
    private val _zoomLevel = MutableLiveData(1f)
    val zoomLevel: LiveData<Float> = _zoomLevel

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    // Implement the ZoomListener interface
    override fun onZoomChange(zoomLevel: Float) {
        setZoomLevel(zoomLevel)
    }

    // Zoom management methods
    fun setZoomLevel(zoomLevel: Float) {
        val constrainedZoom = zoomLevel.coerceIn(0.5f, 5f)
        _zoomLevel.value = constrainedZoom
    }

    fun resetZoom() {
        _zoomLevel.value = 1f
    }

    // Your existing methods...
    fun openPDF(fileDescriptor: ParcelFileDescriptor) {
        try {
            this.fileDescriptor = fileDescriptor
            pdfRenderer = PdfRenderer(fileDescriptor)
            _totalPages.value = pdfRenderer?.pageCount ?: 0
            _currentPage.value = 0
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun nextPage() {
        val currentPage = _currentPage.value ?: 0
        val totalPages = _totalPages.value ?: 0
        if (currentPage < totalPages - 1) {
            _currentPage.value = currentPage + 1
        }
    }

    fun previousPage() {
        val currentPage = _currentPage.value ?: 0
        if (currentPage > 0) {
            _currentPage.value = currentPage - 1
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            // Handle error
        }
    }
}
```

**Key Points:**
- Implement `ZoomListener` interface
- Add `_zoomLevel` MutableLiveData
- Implement `onZoomChange()` method
- Add `setZoomLevel()` and `resetZoom()` methods

---

## Step 4: Update Your Activity

**File:** `YourPDFActivity.kt`

```kotlin
package com.example.yourapp

import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.yourapp.databinding.ActivityPdfBinding
import com.example.yourapp.gestures.PinchZoomGestureListener
import com.example.yourapp.viewmodel.YourPDFViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class YourPDFActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfBinding
    private val viewModel: YourPDFViewModel by viewModels()

    // Gesture handling
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var pinchZoomListener: PinchZoomGestureListener

    // File picker
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadPDF(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the pinch zoom gesture listener with the ViewModel as ZoomListener
        pinchZoomListener = PinchZoomGestureListener(viewModel)
        scaleGestureDetector = ScaleGestureDetector(this, pinchZoomListener)

        setupUIListeners()
        observeViewModel()
    }

    private fun setupUIListeners() {
        binding.apply {
            // Open PDF button
            openPdfButton.setOnClickListener {
                filePicker.launch("application/pdf")
            }

            // Navigation buttons
            nextPageButton.setOnClickListener {
                viewModel.nextPage()
            }

            previousPageButton.setOnClickListener {
                viewModel.previousPage()
            }

            // Zoom buttons
            zoomInButton.setOnClickListener {
                val currentZoom = viewModel.zoomLevel.value ?: 1f
                viewModel.setZoomLevel(currentZoom + 0.5f)
            }

            zoomOutButton.setOnClickListener {
                val currentZoom = viewModel.zoomLevel.value ?: 1f
                viewModel.setZoomLevel((currentZoom - 0.5f).coerceAtLeast(0.5f))
            }

            resetZoomButton.setOnClickListener {
                viewModel.resetZoom()
            }

            // Pinch-to-zoom touch listener
            pdfImageView.setOnTouchListener { _, event ->
                scaleGestureDetector.onTouchEvent(event)
                true
            }
        }
    }

    private fun observeViewModel() {
        viewModel.apply {
            // Observe zoom changes
            zoomLevel.observe(this@YourPDFActivity) { zoom ->
                binding.pdfImageView.scaleX = zoom
                binding.pdfImageView.scaleY = zoom
                binding.zoomIndicator.text = "Zoom: %.1fx".format(zoom)
            }

            // Observe current page
            currentPage.observe(this@YourPDFActivity) { pageIndex ->
                binding.pageIndicator.text =
                    "Page ${pageIndex + 1} of ${totalPages.value ?: 0}"
                // Render the page to the ImageView
                renderPage(pageIndex)
            }

            // Observe total pages
            totalPages.observe(this@YourPDFActivity) { totalPages ->
                binding.pageIndicator.text =
                    "Page ${currentPage.value?.plus(1) ?: 1} of $totalPages"
            }
        }
    }

    private fun loadPDF(uri: Uri) {
        try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                viewModel.openPDF(parcelFileDescriptor)
            } else {
                showError("Failed to open PDF")
            }
        } catch (e: Exception) {
            showError("Error: ${e.message}")
        }
    }

    private fun renderPage(pageIndex: Int) {
        // Use your existing PdfRendererHelper to render the page
        // Then set the bitmap to the ImageView
        // Example:
        // val bitmap = pdfRendererHelper.renderPage(pageIndex)
        // binding.pdfImageView.setImageBitmap(bitmap)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeResources()
    }
}
```

**Key Points:**
- Create `PinchZoomGestureListener(viewModel)` - ViewModel is the ZoomListener
- Set up `scaleGestureDetector` and attach to ImageView via `setOnTouchListener`
- Observe `zoomLevel` LiveData to update ImageView scale
- Button clicks call ViewModel methods
- Use `renderPage()` to display PDF using your existing PdfRendererHelper

---

## Step 5: Update Your XML Layout

**File:** `res/layout/activity_pdf.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/white"
    tools:context=".YourPDFActivity">

    <!-- Top Control Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:background="@color/white"
        android:elevation="4dp">

        <Button
            android:id="@+id/openPdfButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Open PDF"
            android:layout_margin="4dp" />

        <Button
            android:id="@+id/previousPageButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="◄ Prev"
            android:layout_margin="4dp" />

        <Button
            android:id="@+id/nextPageButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Next ►"
            android:layout_margin="4dp" />
    </LinearLayout>

    <!-- PDF Display Container -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@color/black">

        <ScrollView
            android:id="@+id/scrollView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scrollbars="vertical">

            <HorizontalScrollView
                android:id="@+id/horizontalScrollView"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:scrollbars="horizontal">

                <ImageView
                    android:id="@+id/pdfImageView"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:scaleType="fitStart"
                    android:contentDescription="@string/pdf_page" />

            </HorizontalScrollView>

        </ScrollView>

    </FrameLayout>

    <!-- Bottom Control Bar -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="8dp"
        android:background="@color/white"
        android:elevation="4dp">

        <Button
            android:id="@+id/zoomOutButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="−"
            android:layout_margin="4dp" />

        <Button
            android:id="@+id/resetZoomButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Reset"
            android:layout_margin="4dp" />

        <Button
            android:id="@+id/zoomInButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="+"
            android:layout_margin="4dp" />

        <TextView
            android:id="@+id/pageIndicator"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="0.5"
            android:gravity="center"
            android:text="Page 0 of 0"
            android:textSize="12sp"
            android:layout_margin="4dp" />

        <TextView
            android:id="@+id/zoomIndicator"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="0.5"
            android:gravity="center"
            android:text="Zoom: 1.0x"
            android:textSize="12sp"
            android:layout_margin="4dp" />
    </LinearLayout>

</LinearLayout>
```

**Key Points:**
- Use nested `ScrollView` + `HorizontalScrollView` for panning when zoomed
- `ImageView` is the PDF display container
- Buttons for navigation and zoom control
- TextViews for page and zoom indicators

---

## Step 6: Update Your Hilt Module

**File:** `di/PDFModule.kt`

```kotlin
package com.example.yourapp.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PDFModule {
    // Add any shared dependencies here if needed
}
```

**File:** `YourApplication.kt`

```kotlin
package com.example.yourapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class YourApplication : Application()
```

**File:** `AndroidManifest.xml`

```xml
<application
    android:name=".YourApplication"
    android:allowBackup="true"
    ... >
</application>
```

---

## Step 7: Update Build Configuration

**File:** `build.gradle.kts` (Module: app)

```gradle
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.google.dagger.hilt.android")
}

dependencies {
    // Existing dependencies...

    // Hilt
    implementation("com.google.dagger:hilt-android:2.49")
    kapt("com.google.dagger:hilt-compiler:2.49")
}
```

**File:** `build.gradle.kts` (Root)

```gradle
plugins {
    id("com.android.application") version "8.2.0" apply false
    kotlin("android") version "1.9.21" apply false
    kotlin("kapt") version "1.9.21" apply false
    id("com.google.dagger.hilt.android") version "2.49" apply false
}
```

---

## Complete Integration Checklist

- [ ] Create `ZoomListener` interface in `gestures/ZoomListener.kt`
- [ ] Create `PinchZoomGestureListener` in `gestures/PinchZoomGestureListener.kt`
- [ ] Update ViewModel to implement `ZoomListener`
- [ ] Add zoom LiveData to ViewModel
- [ ] Implement `onZoomChange()` in ViewModel
- [ ] Update Activity to instantiate `PinchZoomGestureListener(viewModel)`
- [ ] Set up `ScaleGestureDetector` in Activity
- [ ] Add touch listener to ImageView
- [ ] Observe zoom LiveData and update UI
- [ ] Update XML layout with ImageView and controls
- [ ] Set up Hilt in Application class
- [ ] Update manifest with application name
- [ ] Add Hilt dependencies to build.gradle

---

## How It Works

1. **User pinches on screen** → `ScaleGestureDetector` detects gesture
2. **Gesture detected** → `PinchZoomGestureListener.onScale()` is called
3. **Calculate new zoom** → Gesture listener computes new zoom level
4. **Call callback** → `zoomListener.onZoomChange(newZoom)` is invoked
5. **ViewModel updates** → `PDFViewModel.onZoomChange()` updates LiveData
6. **UI updates** → Activity observes LiveData and updates ImageView scale

---

## Using with Multiple ViewModels

To use the same gesture listener with another ViewModel:

```kotlin
class SecondViewModel : ViewModel(), ZoomListener {
    private val _zoomLevel = MutableLiveData(1f)
    val zoomLevel: LiveData<Float> = _zoomLevel

    override fun onZoomChange(zoomLevel: Float) {
        setZoomLevel(zoomLevel)
    }

    fun setZoomLevel(zoomLevel: Float) {
        _zoomLevel.value = zoomLevel.coerceIn(0.5f, 5f)
    }
}

// In second Activity:
pinchZoomListener = PinchZoomGestureListener(secondViewModel)
scaleGestureDetector = ScaleGestureDetector(this, pinchZoomListener)
```

---

## Troubleshooting

**Pinch-to-zoom not working:**
- Ensure `setOnTouchListener` is set on the ImageView
- Check that `scaleGestureDetector.onTouchEvent(event)` is called

**Zoom not updating UI:**
- Verify ViewModel implements `ZoomListener`
- Check that `onZoomChange()` calls `setZoomLevel()`
- Ensure Activity observes `viewModel.zoomLevel`

**Hilt errors:**
- Add `@HiltAndroidApp` to Application class
- Update manifest with application name
- Ensure all dependencies are in build.gradle

---

## Summary

This implementation provides:
- ✅ Pinch-to-zoom gesture handling
- ✅ Zoom state management via ViewModel
- ✅ Reusable across multiple ViewModels
- ✅ Clean separation of concerns
- ✅ LiveData for reactive UI updates
- ✅ Hilt dependency injection
