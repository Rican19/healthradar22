package com.capstone.healthradar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
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
    private lateinit var fabScrollToTop: FloatingActionButton
    private lateinit var errorLayout: View
    private lateinit var toolbar: MaterialToolbar

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
        recyclerView = view.findViewById(R.id.recyclerViewNews)
        progressBar = view.findViewById(R.id.progressBar)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        fabScrollToTop = view.findViewById(R.id.fabScrollToTop)
        errorLayout = view.findViewById(R.id.errorLayout)
        toolbar = view.findViewById(R.id.toolbar)

        // Setup retry button
        errorLayout.findViewById<View>(R.id.buttonRetry).setOnClickListener {
            fetchNews()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Show/hide FAB based on scroll position
                if (dy > 0 && fabScrollToTop.visibility == View.VISIBLE) {
                    fabScrollToTop.hide()
                } else if (dy < 0 && fabScrollToTop.visibility != View.VISIBLE) {
                    fabScrollToTop.show()
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
        swipeRefreshLayout.setOnRefreshListener {
            fetchNews()
        }
    }

    private fun setupFab() {
        fabScrollToTop.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
    }

    private fun setupToolbar() {
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
        errorLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getHealthNews()
                if (response.articles.isNotEmpty()) {
                    val adapter = NewsAdapter(requireContext(), response.articles) { article ->
                        // Handle item click - open in browser or detail screen
                        openNewsDetail(article)
                    }
                    recyclerView.adapter = adapter
                    showSuccess()
                } else {
                    showError("No news available")
                }
            } catch (e: Exception) {
                showError("Failed to load news: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showSuccess() {
        recyclerView.visibility = View.VISIBLE
        errorLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        recyclerView.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun openNewsDetail(article: NewsArticle) {
        // Implement opening news in browser or detail fragment
        // For now, show a toast
        Toast.makeText(requireContext(), "Opening: ${article.title}", Toast.LENGTH_SHORT).show()

        // You can add intent to open browser:
        /*
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
        startActivity(intent)
        */
    }
}