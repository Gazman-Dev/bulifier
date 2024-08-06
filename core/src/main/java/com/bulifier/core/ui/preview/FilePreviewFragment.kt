package com.bulifier.core.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilePreviewFragment : Fragment() {

    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = WebView(requireContext()).apply {
        webView = this
        webChromeClient = WebChromeClient()
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val assetManager = requireContext().assets
                val inputStream = assetManager.open("preview.html")
                val htmlContent = inputStream.bufferedReader().use { it.readText() }

                val content = "```kotlin\nfun main() {\n    println(\"Hello, world!\")\n}\n```"
                val formattedHtml = htmlContent.format(content)

                withContext(Dispatchers.Main) {
                    webView.loadDataWithBaseURL(null, formattedHtml, "text/html", "UTF-8", null)
                }
            }
        }

    }
}