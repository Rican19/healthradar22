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
import com.google.android.material.card.MaterialCardView
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

        holder.cardView?.setOnClickListener {
            onItemClick(article)
        }

        holder.buttonReadMore?.setOnClickListener {
            onItemClick(article)
        }
    }

    override fun getItemCount(): Int = articles.size

    fun updateData(newArticles: List<NewsArticle>) {
        articles = newArticles
        notifyDataSetChanged()
    }

    inner class NewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Make all views nullable and use safe initialization
        private val imageView: ImageView? = itemView.findViewById(R.id.imageViewNews)
        private val titleTextView: TextView? = itemView.findViewById(R.id.textViewTitle)
        private val descriptionTextView: TextView? = itemView.findViewById(R.id.textViewDescription)
        private val sourceTextView: TextView? = itemView.findViewById(R.id.textViewSource)
        private val timeTextView: TextView? = itemView.findViewById(R.id.textViewTime)
        val buttonReadMore: MaterialButton? = itemView.findViewById(R.id.buttonReadMore)
        val cardView: MaterialCardView? = itemView.findViewById(R.id.cardView)

        fun bind(article: NewsArticle) {
            // Safely set text on views that exist
            titleTextView?.text = article.title ?: "No Title"
            descriptionTextView?.text = article.description ?: "No description available"
            sourceTextView?.text = article.source?.name ?: "Unknown Source"

            // Format published date
            article.publishedAt?.let {
                timeTextView?.text = formatDate(it)
            } ?: run {
                timeTextView?.text = "Unknown time"
            }

            // Apply text colors to views that exist
            applyTextColors()

            // Load image if imageView exists
            loadImage(article.urlToImage)
        }

        private fun applyTextColors() {
            // Safely apply colors only if views exist
            titleTextView?.setTextColor(ContextCompat.getColor(context, R.color.dark_text))
            descriptionTextView?.setTextColor(ContextCompat.getColor(context, R.color.medium_gray))
            sourceTextView?.setTextColor(ContextCompat.getColor(context, R.color.primary_color))
            timeTextView?.setTextColor(ContextCompat.getColor(context, R.color.light_gray))

            // Style the read more button if it exists
            buttonReadMore?.setBackgroundColor(ContextCompat.getColor(context, R.color.primary_color))
            buttonReadMore?.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        private fun loadImage(imageUrl: String?) {
            // Only load image if imageView exists
            imageView?.let { imgView ->
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(context)
                        .load(imageUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .placeholder(android.R.color.darker_gray)
                        .error(android.R.color.darker_gray)
                        .centerCrop()
                        .into(imgView)
                } else {
                    // Set placeholder when no image URL
                    imgView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    imgView.setImageDrawable(null)
                }
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val date = inputFormat.parse(dateString)
                    outputFormat.format(date ?: Date())
                } catch (e2: Exception) {
                    "Recent"
                }
            }
        }
    }
}