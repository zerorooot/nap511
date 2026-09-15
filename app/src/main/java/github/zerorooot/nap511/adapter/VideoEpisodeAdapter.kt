package github.zerorooot.nap511.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.VideoBean

class VideoEpisodeAdapter(
    private var videoList: List<VideoBean> = emptyList(),
    private var currentPlayingIndex: Int = -1,
    private val onItemClick: (Int, VideoBean) -> Unit
) : RecyclerView.Adapter<VideoEpisodeAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardEpisode: CardView = itemView.findViewById(R.id.card_episode)
        val tvPlayingIcon: TextView = itemView.findViewById(R.id.tv_playing_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_episode_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_episode, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = videoList[position]
        val displayIndex = position + 1
        val titleText = if (item.name.isNotEmpty()) {
            "$displayIndex. ${item.name}"
        } else {
            "第 $displayIndex 集"
        }
        holder.tvTitle.text = titleText

        if (position == currentPlayingIndex) {
            holder.cardEpisode.setCardBackgroundColor(Color.parseColor("#3342A5F5"))
            holder.tvPlayingIcon.visibility = View.VISIBLE
            holder.tvTitle.setTypeface(null, Typeface.BOLD)
            holder.tvTitle.setTextColor(Color.parseColor("#42A5F5"))
        } else {
            holder.cardEpisode.setCardBackgroundColor(Color.parseColor("#1AFFFFFF"))
            holder.tvPlayingIcon.visibility = View.GONE
            holder.tvTitle.setTypeface(null, Typeface.NORMAL)
            holder.tvTitle.setTextColor(Color.parseColor("#D0FFFFFF"))
        }

        holder.itemView.setOnClickListener {
            onItemClick(position, item)
        }
    }

    override fun getItemCount(): Int = videoList.size

    fun updateData(newList: List<VideoBean>, newIndex: Int) {
        videoList = newList
        currentPlayingIndex = newIndex
        notifyDataSetChanged()
    }

    fun updateCurrentIndex(newIndex: Int) {
        if (currentPlayingIndex != newIndex) {
            val oldIndex = currentPlayingIndex
            currentPlayingIndex = newIndex
            if (oldIndex in videoList.indices) notifyItemChanged(oldIndex)
            if (newIndex in videoList.indices) notifyItemChanged(newIndex)
        }
    }
}
