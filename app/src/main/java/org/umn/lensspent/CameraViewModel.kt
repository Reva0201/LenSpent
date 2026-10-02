package org.umn.lensspent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.umn.lensspent.data.model.CaptureSource
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * Records images the camera screen produces, so a photo or screenshot exists in the database from the
 * moment it is taken. The extraction stages (detector -> OCR -> structurer) hang off these same rows.
 */
class CameraViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _pendingCaptureId = MutableStateFlow<Long?>(null)

    /**
     * Set once an image has been stored and is ready to be reviewed.
     *
     * Exposed as state rather than an event callback so the navigation survives a rotation: the screen
     * navigates, then calls [consumePendingCapture] so the same capture is not opened twice.
     */
    val pendingCaptureId: StateFlow<Long?> = _pendingCaptureId.asStateFlow()

    fun onImageCaptured(sourceUri: String, source: CaptureSource) {
        viewModelScope.launch {
            val captureId = repository.recordCapture(sourceUri = sourceUri, source = source)
            if (captureId > 0L) {
                _pendingCaptureId.value = captureId
            }
        }
    }

    fun consumePendingCapture() {
        _pendingCaptureId.value = null
    }
}
