package github.zerorooot.nap511.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import github.zerorooot.nap511.R

class VideoOptionAdapter(
    private var options: List<String>,
    private var selectedIndex: Int = 0,
    private val isOptionSelectable: ((Int) -> Boolean)? = null,
    private val onOptionSelected: (Int, String) -> Unit
) : RecyclerView.Adapter<VideoOptionAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardOption: CardView = itemView.findViewById(R.id.card_option)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val titleText = options[position]
        holder.tvTitle.text = titleText

        // 判断当前项是否具备单选高亮属性（如非单选操作项不显示选中态）
        val isSelectable = isOptionSelectable?.invoke(position) ?: true
        if (isSelectable && position == selectedIndex) {
            holder.cardOption.setCardBackgroundColor("#3342A5F5".toColorInt())
            holder.tvTitle.setTypeface(null, Typeface.BOLD)
            holder.tvTitle.setTextColor("#42A5F5".toColorInt())
        } else {
            holder.cardOption.setCardBackgroundColor("#1AFFFFFF".toColorInt())
            holder.tvTitle.setTypeface(null, Typeface.NORMAL)
            holder.tvTitle.setTextColor("#D0FFFFFF".toColorInt())
        }

        holder.itemView.setOnClickListener {
            if (isSelectable && selectedIndex != position) {
                val oldIndex = selectedIndex
                selectedIndex = position
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
            }
            onOptionSelected(position, titleText)
        }
    }

    override fun getItemCount(): Int = options.size

    fun getSelectedIndex(): Int = selectedIndex

    fun getOptions(): List<String> = options

    fun isOptionSelectable(position: Int): Boolean = isOptionSelectable?.invoke(position) ?: true

    fun updateSelectedIndex(newIndex: Int) {
        if (selectedIndex != newIndex) {
            val oldIndex = selectedIndex
            selectedIndex = newIndex
            if (oldIndex in options.indices) notifyItemChanged(oldIndex)
            if (newIndex in options.indices) notifyItemChanged(newIndex)
        }
    }

    fun updateData(newOptions: List<String>, newIndex: Int = selectedIndex) {
        options = newOptions
        selectedIndex = newIndex
        notifyDataSetChanged()
    }
}
