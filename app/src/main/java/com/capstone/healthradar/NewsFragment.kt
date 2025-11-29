package com.capstone.healthradar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class NewsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var toolbar: MaterialToolbar

    // Make these nullable and initialize them safely
    private var fabScrollToTop: FloatingActionButton? = null
    private var errorLayout: View? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_news, container, false)

        initializeViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        setupToolbar()
        fetchNews()

        return view
    }

    private fun initializeViews(view: View) {
        // Initialize main views (these should exist in your layout)
        recyclerView = view.findViewById(R.id.recyclerViewNews)
        progressBar = view.findViewById(R.id.progressBar)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        toolbar = view.findViewById(R.id.toolbar)

        // Safely initialize optional views - check if they exist in layout
        fabScrollToTop = view.findViewById(R.id.fabScrollToTop) // This might not exist
        errorLayout = view.findViewById(R.id.errorLayout) // This might not exist

        // Setup retry button if error layout exists
        errorLayout?.let { errorView ->
            val retryButton = errorView.findViewById<TextView?>(R.id.buttonRetry)
            retryButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            retryButton?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
            retryButton?.setOnClickListener {
                fetchNews()
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Only handle FAB if it exists
                fabScrollToTop?.let { fab ->
                    if (dy > 0 && fab.visibility == View.VISIBLE) {
                        fab.hide()
                    } else if (dy < 0 && fab.visibility != View.VISIBLE) {
                        fab.show()
                    }
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        // Set light theme colors for swipe refresh
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(requireContext(), R.color.white)
        )
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.primary_color),
            ContextCompat.getColor(requireContext(), R.color.accent_color),
            ContextCompat.getColor(requireContext(), R.color.success_color),
            ContextCompat.getColor(requireContext(), R.color.warning_color)
        )
        swipeRefreshLayout.setOnRefreshListener {
            fetchNews()
        }
    }

    private fun setupFab() {
        // Only setup FAB if it exists
        fabScrollToTop?.let { fab ->
            fab.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
            fab.setOnClickListener {
                recyclerView.smoothScrollToPosition(0)
            }
        }
    }

    private fun setupToolbar() {
        toolbar.setTitleTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    fetchNews()
                    true
                }
                else -> false
            }
        }
    }

    private fun fetchNews() {
        progressBar.visibility = View.VISIBLE
        // Hide error layout if it exists
        errorLayout?.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getHealthNews()
                if (response.articles.isNotEmpty()) {
                    val currentAdapter = recyclerView.adapter
                    if (currentAdapter is NewsAdapter) {
                        currentAdapter.updateData(response.articles)
                    } else {
                        val adapter = NewsAdapter(requireContext(), response.articles) { article ->
                            openNewsDetail(article)
                        }
                        recyclerView.adapter = adapter
                    }
                    showSuccess()
                } else {
                    showError("No News Available", "There are no health news articles at the moment.")
                }
            } catch (e: Exception) {
                showError("Connection Error", "Failed to load news. Please check your internet connection.")
                e.printStackTrace()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showSuccess() {
        recyclerView.visibility = View.VISIBLE
        errorLayout?.visibility = View.GONE
    }

    private fun showError(title: String, message: String) {
        recyclerView.visibility = View.GONE
        // If error layout doesn't exist, just show a toast
        if (errorLayout == null) {
            Toast.makeText(requireContext(), "$title: $message", Toast.LENGTH_LONG).show()
        } else {
            errorLayout?.visibility = View.VISIBLE
            // You can add custom error handling here if needed
        }
    }

    private fun openNewsDetail(article: NewsArticle) {
        var url = article.url

        if (url.isNullOrBlank()) {
            Toast.makeText(requireContext(), "No link available for this article", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure URL has proper protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        // Try multiple approaches to open the link
        if (!tryOpenWithBrowser(url) && !tryOpenWithCustomTabs(url)) {
            // If all else fails, show options to user
            showNoBrowserOptions(url)
        }
    }

    private fun tryOpenWithBrowser(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Check if there's an app that can handle this intent
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun tryOpenWithCustomTabs(url: String): Boolean {
        return try {
            // Try with a more generic intent that might work on some devices
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Add category to make it more likely to find a handler
            intent.addCategory(Intent.CATEGORY_BROWSABLE)

            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun showNoBrowserOptions(url: String) {
        val options = arrayOf("Copy Link to Clipboard", "View in Simple Browser")

        AlertDialog.Builder(requireContext())
            .setTitle("Cannot Open Link")
            .setMessage("No browser app found. Choose an option:")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> copyToClipboard(url)
                    1 -> showSimpleWebView(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyToClipboard(url: String) {
        try {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("News Article URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Link copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to copy link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSimpleWebView(url: String) {
        try {
            // Create a simple dialog with WebView
            val dialog = android.app.AlertDialog.Builder(requireContext()).create()
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_webview, null)

            val webView = dialogView.findViewById<WebView>(R.id.webView)
            val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
            val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

            // Configure WebView
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.setSupportZoom(true)
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                }
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            webView.loadUrl(url)

            dialog.setView(dialogView)
            dialog.show()

            // Set dialog window size
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot display web content", Toast.LENGTH_SHORT).show()
            // Fallback to clipboard
            copyToClipboard(url)
        }
    }
}