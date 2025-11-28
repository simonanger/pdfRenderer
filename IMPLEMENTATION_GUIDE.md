# PDF Renderer with Pinch-to-Zoom Implementation Guide (RecyclerView)

This guide explains how to integrate pinch-to-zoom functionality into your existing Android app that displays PDFs in a RecyclerView. The implementation uses your existing ZoomListener interface.

---

## Quick Summary

You have:
- ✅ Activity with `onCreate` and `onBackPressed`
- ✅ ViewModel that handles PDF rendering with `_pdfPages` LiveData
- ✅ PdfRendererHelper with `renderPdf()`, `renderPages()`, `renderPage()`
- ✅ RecyclerView displaying PDF pages as ImageViews
- ✅ ZoomListener interface already exists

You need to add:
- 3 things to ViewModel (interface + LiveData + methods)
- 4 lines to Activity onCreate (gesture setup + observer)
- 1 gesture listener class
- 1 small change to RecyclerView adapter

---

## Step 1: Add These Imports to Your ViewModel

```kotlin
import android.view.ScaleGestureDetector
import com.yourapp.gestures.ZoomListener  // Your existing interface
```

---

## Step 2: Make ViewModel Implement ZoomListener

**Current:**
```kotlin
class YourPDFViewModel : ViewModel() {
```

**Change to:**
```kotlin
class YourPDFViewModel : ViewModel(), ZoomListener {
```

---

## Step 3: Add Zoom LiveData to Your ViewModel

Add this property alongside your existing `_pdfPages`:

```kotlin
private val _zoomLevel = MutableLiveData(1f)
val zoomLevel: LiveData<Float> = _zoomLevel
```

---

## Step 4: Implement ZoomListener in Your ViewModel

Add this method to your ViewModel:

```kotlin
override fun onZoomChange(zoomLevel: Float) {
    setZoomLevel(zoomLevel)
}

fun setZoomLevel(zoomLevel: Float) {
    val constrainedZoom = zoomLevel.coerceIn(0.5f, 5f)
    _zoomLevel.value = constrainedZoom
}

fun resetZoom() {
    _zoomLevel.value = 1f
}
```

---

## Step 5: Create the PinchZoomGestureListener Class

**File:** `gestures/PinchZoomGestureListener.kt`

```kotlin
package com.yourapp.gestures

import android.view.ScaleGestureDetector
import com.yourapp.gestures.ZoomListener  // Your existing interface

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

---

## Step 6: Update Your Activity's onCreate

Add these imports:
```kotlin
import android.view.ScaleGestureDetector
import com.yourapp.gestures.PinchZoomGestureListener
```

In your `onCreate()` method, add this code after setting up the RecyclerView:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Your existing code...

    // AFTER your RecyclerView setup, ADD THIS:
    val pinchZoomListener = PinchZoomGestureListener(viewModel)
    val scaleGestureDetector = ScaleGestureDetector(this, pinchZoomListener)

    // Get reference to your RecyclerView
    binding.pdfRecyclerView.setOnTouchListener { _, event ->
        scaleGestureDetector.onTouchEvent(event)
        false  // Let RecyclerView handle the touch after
    }

    // Observe zoom changes
    viewModel.zoomLevel.observe(this) { zoom ->
        // Notify adapter to update all visible PDF ImageViews
        (binding.pdfRecyclerView.adapter as? YourPDFAdapter)?.setZoom(zoom)
    }

    // Your existing observers...
}
```

---

## Step 7: Update Your RecyclerView Adapter

In your PDF RecyclerView Adapter, add these methods:

```kotlin
class YourPDFAdapter : RecyclerView.Adapter<YourViewHolder>() {

    private var currentZoom = 1f

    override fun onBindViewHolder(holder: YourViewHolder, position: Int) {
        // Your existing bind code...

        // AFTER displaying the bitmap, ADD THIS:
        holder.pdfImageView.scaleX = currentZoom
        holder.pdfImageView.scaleY = currentZoom
    }

    // ADD THIS NEW METHOD:
    fun setZoom(zoomLevel: Float) {
        currentZoom = zoomLevel
        notifyItemRangeChanged(0, itemCount)  // Update all visible items
    }
}
```

---

## Alternative: Only Zoom the Currently Visible Page

If you want to zoom only the page in view, use this instead:

```kotlin
// In Activity onCreate:
viewModel.zoomLevel.observe(this) { zoom ->
    val layoutManager = binding.pdfRecyclerView.layoutManager as LinearLayoutManager
    val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
    if (firstVisiblePosition != RecyclerView.NO_POSITION) {
        val holder = binding.pdfRecyclerView.findViewHolderForAdapterPosition(firstVisiblePosition)
        (holder as? YourViewHolder)?.apply {
            pdfImageView.scaleX = zoom
            pdfImageView.scaleY = zoom
        }
    }
}
```

---

## Step 8: Update Your PdfRendererHelper (Optional)

No changes needed! Your existing `renderPdf()`, `renderPages()`, and `renderPage()` continue to work as-is. The zoom is applied after the bitmaps are displayed.

---

## Testing Your Implementation

1. Run your app
2. Open a PDF
3. Place two fingers on the PDF in RecyclerView
4. Pinch in/out to zoom
5. Should see PDF scale from 0.5x to 5x
6. Zoom resets when you load a new PDF

---

## Complete File Checklist

- [ ] ZoomListener interface exists in `gestures/ZoomListener.kt`
- [ ] Created `gestures/PinchZoomGestureListener.kt`
- [ ] ViewModel implements `ZoomListener`
- [ ] ViewModel has `_zoomLevel` LiveData
- [ ] ViewModel has `setZoomLevel()` and `resetZoom()` methods
- [ ] Activity imports `ScaleGestureDetector` and `PinchZoomGestureListener`
- [ ] Activity setUp `scaleGestureDetector` in onCreate
- [ ] Activity observes `viewModel.zoomLevel`
- [ ] RecyclerView adapter has `setZoom()` method
- [ ] RecyclerView adapter scales ImageView in `onBindViewHolder`

---

## How It Works (Step-by-Step)

1. User pinches on RecyclerView
2. `ScaleGestureDetector` detects the pinch gesture
3. Calls `PinchZoomGestureListener.onScale()`
4. Gesture listener calculates new zoom level
5. Calls `zoomListener.onZoomChange(newZoom)`
6. ViewModel's `onZoomChange()` is invoked
7. ViewModel updates `_zoomLevel` LiveData
8. Activity observes the change
9. Activity calls adapter's `setZoom(zoom)`
10. Adapter updates all visible PDF ImageViews with new scale

---

## Troubleshooting

**Pinch-to-zoom not working:**
- Ensure `setOnTouchListener` is set on RecyclerView
- Check that `scaleGestureDetector.onTouchEvent(event)` is called
- Verify listener returns `false` to allow RecyclerView to handle touch

**Zoom not updating:**
- Check ViewModel implements `ZoomListener`
- Verify adapter has `setZoom()` method
- Ensure Activity observes `viewModel.zoomLevel`

**Only first page zooms:**
- Use the "Alternative" approach above to zoom only visible page
- Or ensure adapter calls `notifyItemRangeChanged(0, itemCount)`

**Zoom resets unexpectedly:**
- Check if you're re-rendering pages (which resets zoom)
- Add `viewModel.setZoomLevel(currentZoom)` if re-rendering

---

## Summary

This implementation provides:
- ✅ Pinch-to-zoom for RecyclerView PDFs
- ✅ Works with your existing code
- ✅ Zoom state managed in ViewModel
- ✅ Minimal changes required
- ✅ No breaking changes to existing functionality
