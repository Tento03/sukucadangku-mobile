package com.sukucadangku.mobile

import androidx.lifecycle.ViewModel
import com.google.accompanist.web.WebViewNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _webViewUrl = MutableStateFlow(BuildConfig.BASE_URL)
    val webViewUrl: StateFlow<String> = _webViewUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private var navigator: WebViewNavigator? = null

    fun setNavigator(nav: WebViewNavigator?) {
        navigator = nav
    }

    fun handleBackPress(): Boolean {
        return if (navigator?.canGoBack == true) {
            navigator?.navigateBack()
            true
        } else {
            false
        }
    }

    fun onPageStarted() {
        _isLoading.value = true
        _hasError.value = false
    }

    fun onPageFinished() {
        _isLoading.value = false
    }

    fun onError() {
        _isLoading.value = false
        _hasError.value = true
    }

    fun onRetry() {
        _hasError.value = false
        _isLoading.value = true
        navigator?.reload()
    }

    fun updateCanGoBack(canGoBack: Boolean) {
        _canGoBack.value = canGoBack
    }
}
