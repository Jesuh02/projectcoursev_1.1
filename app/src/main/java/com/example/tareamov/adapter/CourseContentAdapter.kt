package com.example.tareamov.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tareamov.R
import com.example.tareamov.data.entity.ContentItem

class CourseContentAdapter(
    private val context: Context,
    private var contentItems: List<ContentItem>,
    private val onItemClickListener: (ContentItem) -> Unit
) : RecyclerView.Adapter<CourseContentAdapter.ContentViewHolder>() {

    class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.contentIconView)
        val nameTextView: TextView = itemView.findViewById(R.id.contentNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course_content_detail, parent, false)
        return ContentViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        val contentItem = contentItems[position]

        holder.nameTextView.text = contentItem.name

        // Set appropriate icon based on content type
        val iconResource = when (contentItem.contentType) {
            "video" -> android.R.drawable.ic_media_play
            "document" -> android.R.drawable.ic_menu_edit
            "task" -> android.R.drawable.ic_menu_agenda
            else -> android.R.drawable.ic_menu_info_details
        }
        holder.iconView.setImageResource(iconResource)

        // Set click listener
        holder.itemView.setOnClickListener {
            onItemClickListener(contentItem)
        }
    }

    override fun getItemCount(): Int = contentItems.size

    fun updateContent(newContent: List<ContentItem>) {
        contentItems = newContent
        notifyDataSetChanged()
    }
}