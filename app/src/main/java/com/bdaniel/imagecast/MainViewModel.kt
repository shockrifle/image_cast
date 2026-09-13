package com.bdaniel.imagecast

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val cast = CastController(application, viewModelScope)

    private val _image = MutableStateFlow<LoadedImage?>(null)
    val image: StateFlow<LoadedImage?> = _image.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _transform = MutableStateFlow(ViewTransform.IDENTITY)
    val transform: StateFlow<ViewTransform> = _transform.asStateFlow()

    fun pickImage(uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val context = getApplication<Application>()
            runCatching {
                withContext(Dispatchers.IO) {
                    // Keep read access across process death / permission grants.
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val loaded = ImageLoading.load(context, uri)
                    loaded to ImageLoading.encodeForCast(loaded)
                }
            }.onSuccess { (loaded, payload) ->
                // The previous bitmap is left to the GC: it may still be held by
                // a composition that has not been recycled out yet.
                _image.value = loaded
                resetTransform()
                cast.setImage(payload)
            }.onFailure {
                _error.value = it.message ?: "Could not open that image"
            }
            _loading.value = false
        }
    }

    fun updateTransform(transform: ViewTransform) {
        _transform.value = transform
        cast.setTransform(transform)
    }

    fun resetTransform() {
        updateTransform(ViewTransform.IDENTITY)
    }

    fun dismissError() {
        _error.value = null
    }

    override fun onCleared() {
        cast.release()
        super.onCleared()
    }
}
