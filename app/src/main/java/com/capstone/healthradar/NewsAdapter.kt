package com.capstone.healthradar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class NewsAdapter(
    private val context: Context,
    private var articles: List<NewsArticle>,
    private val onItemClick: (NewsArticle) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.items_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val article = articles[position]
        holder.bind(article)

        holder.itemView.setOnClickListener {
            onItemClick(article)
        }

        holder.buttonReadMore.setOnClickListener {
            onItemClick(article)
        }
    }

    override fun getItemCount(): Int = articles.size

    inner class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageViewNews)
        private val titleTextView: TextView = itemView.findViewById(R.id.textViewTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.textViewDescription)
        private val sourceTextView: TextView = itemView.findViewById(R.id.textViewSource)
        private val timeTextView: TextView = itemView.findViewById(R.id.textViewTime)
        val buttonReadMore: MaterialButton = itemView.findViewById(R.id.buttonReadMore)

        fun bind(article: NewsArticle) {
            titleTextView.text = article.title ?: "No Title"
            descriptionTextView.text = article.description ?: "No description available"
            sourceTextView.text = article.source?.name ?: "Unknown Source"

            // Format published date
            article.publishedAt?.let {
                timeTextView.text = formatDate(it)
            } ?: run {
                timeTextView.text = "Unknown time"
            }

            // Load image with Glide - SIMPLIFIED VERSION
            loadImage(article.urlToImage)
        }

        private fun loadImage(imageUrl: String?) {
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(context)
                    .load(imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.placeholder_background) // Use your placeholder
                    .error(R.drawable.placeholder_background) // Use same for error
                    .into(imageView) // This should work now
            } else {
                // Set placeholder when no image URL
                imageView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                // Or set a placeholder drawable
                // imageView.setImageResource(R.drawable.placeholder_background)
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                "Invalid date"
            }
        }
    }
}