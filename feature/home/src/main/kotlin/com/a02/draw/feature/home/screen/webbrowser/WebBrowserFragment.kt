package com.a02.draw.feature.home.screen.webbrowser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.databinding.ScreenWebBrowserBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WebBrowserFragment : BaseFragment<ScreenWebBrowserBinding>(ScreenWebBrowserBinding::inflate) {
    override val useScreenTransitions: Boolean = false

    private val viewModel: WebBrowserViewModel by viewModels()
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.webView.canGoBack()) binding.webView.goBack() else findNavController().navigateUp()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun setupViews(savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.toolbar.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener { backCallback.handleOnBackPressed() }
        binding.searchButton.setDebouncedClickListener { submitSearch() }
        binding.retryButton.setDebouncedClickListener {
            binding.pageErrorGroup.isVisible = false
            binding.webView.reload()
        }
        binding.importErrorDismiss.setDebouncedClickListener { viewModel.onAction(WebBrowserAction.ClearError) }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else false
        }
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }
        binding.webView.addJavascriptInterface(ImageSelectionBridge(), BRIDGE_NAME)
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.pageProgress.progress = newProgress
                binding.pageProgress.isVisible = newProgress in 1..99
            }
        }
        binding.webView.webViewClient = BrowserClient()
        binding.searchInput.setText(viewModel.state.value.query)
        binding.searchInput.setSelection(binding.searchInput.text?.length ?: 0)
        if (viewModel.state.value.query.isNotBlank()) {
            binding.emptyGroup.isVisible = false
            binding.webView.isVisible = true
            DrawingImageSearchUrlBuilder.build(viewModel.state.value.query)
                ?.let(binding.webView::loadUrl)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is WebBrowserEffect.LoadUrl -> {
                            binding.emptyGroup.isVisible = false
                            binding.pageErrorGroup.isVisible = false
                            binding.webView.isVisible = true
                            binding.webView.loadUrl(effect.url)
                        }

                        WebBrowserEffect.NavigateToDrawingMode -> findNavController().navigate(R.id.tutorialCameraFragment)
                    }
                }
            }
        }
    }

    private fun render(state: WebBrowserUiState) {
        binding.importingOverlay.isVisible = state.isImporting
        binding.importErrorGroup.isVisible = state.importError != null
        binding.importErrorMessage.text = state.importError
    }

    private fun submitSearch() {
        viewModel.onAction(WebBrowserAction.Search(binding.searchInput.text?.toString().orEmpty()))
    }

    private inner class ImageSelectionBridge {
        @JavascriptInterface
        fun onImageSelected(url: String?) {
            val safe = url?.take(MAX_URL_LENGTH) ?: return
            binding.root.post { viewModel.onAction(WebBrowserAction.ImageSelected(safe)) }
        }
    }

    private inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest
        ): Boolean {
            val uri = request.url
            return uri.scheme != "https" || !isAllowedPageHost(uri.host.orEmpty())
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            binding.pageErrorGroup.isVisible = false
            binding.emptyGroup.isVisible = false
            binding.webView.isVisible = true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            injectImageSelection(view)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest,
            error: WebResourceError?,
        ) {
            if (!request.isForMainFrame) return
            binding.webView.isVisible = false
            binding.pageErrorGroup.isVisible = true
        }
    }

    private fun injectImageSelection(webView: WebView) {
        webView.evaluateJavascript(IMAGE_SELECTION_SCRIPT, null)
    }

    override fun onDestroyView() {
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeJavascriptInterface(BRIDGE_NAME)
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroyView()
    }

    private companion object {
        const val BRIDGE_NAME = "A02ImagePicker"
        const val MAX_URL_LENGTH = 8_192
        val IMAGE_SELECTION_SCRIPT = """
            (function() {
              document.querySelectorAll('img').forEach(function(img) {
                if (img.dataset.a02Bound === '1') return;
                img.dataset.a02Bound = '1';
                img.addEventListener('click', function(event) {
                  event.preventDefault(); event.stopPropagation();
                  var candidate = img.currentSrc || img.src || img.getAttribute('data-src');
                  var anchor = img.closest('a');
                  if (anchor && anchor.href) {
                    try { candidate = new URL(anchor.href).searchParams.get('imgurl') || candidate; } catch (_) {}
                  }
                  if (candidate) window.A02ImagePicker.onImageSelected(candidate);
                }, true);
              });
            })();
        """.trimIndent()

        fun isAllowedPageHost(host: String): Boolean {
            val value = host.lowercase()
            return value == "google.com" || value.endsWith(".google.com") ||
                    value.startsWith("www.google.") || value.startsWith("consent.google.")
        }
    }
}
