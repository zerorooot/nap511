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

class VideoOptionAdapter(
    private var options: List<String>,
    private var selectedIndex: Int = 0,
    private val onOptionSelected: (Int, String) -> Unit
) : RecyclerView.Adapter<VideoOptionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardOption: CardView = itemView.findViewById(R.id.card_option)
        val tvOptionTitle: TextView = itemView.findViewById(R.id.tv_option_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val title = options[position]
        holder.tvOptionTitle.text = title

        if (position == selectedIndex) {
            holder.cardOption.setCardBackgroundColor(Color.parseColor("#3342A5F5"))
            holder.tvOptionTitle.setTypeface(null, Typeface.BOLD)
            holder.tvOptionTitle.setTextColor(Color.parseColor("#42A5F5"))
        } else {
            holder.cardOption.setCardBackgroundColor(Color.parseColor("#1AFFFFFF"))
            holder.tvOptionTitle.setTypeface(null, Typeface.NORMAL)
            holder.tvOptionTitle.setTextColor(Color.parseColor("#D0FFFFFF"))
        }

        holder.itemView.setOnClickListener {
            if (selectedIndex != position) {
                val oldIndex = selectedIndex
                selectedIndex = position
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
            }
            onOptionSelected(position, title)
        }
    }

    override fun getItemCount(): Int = options.size

    fun updateSelectedIndex(newIndex: Int) {
        if (selectedIndex != newIndex && newIndex in options.indices) {
            val oldIndex = selectedIndex
            selectedIndex = newIndex
            notifyItemChanged(oldIndex)
            notifyItemChanged(selectedIndex)
        }
    }
}
