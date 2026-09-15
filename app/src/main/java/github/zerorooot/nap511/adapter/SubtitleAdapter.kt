package github.zerorooot.nap511.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleSourceType
import java.util.Locale

/**
 * 字幕列表 RecyclerView 适配器
 */
class SubtitleAdapter(
    private var items: List<SubtitleItem> = emptyList(),
    private var selectedId: String = "",
    private val onUploadClick: ((SubtitleItem) -> Unit)? = null,
    private val onItemClick: (SubtitleItem) -> Unit
) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardSubtitle: CardView = itemView.findViewById(R.id.card_subtitle)
        val tvName: TextView = itemView.findViewById(R.id.tv_subtitle_name)
        val tvSource: TextView = itemView.findViewById(R.id.tv_subtitle_source)
        val tvExt: TextView = itemView.findViewById(R.id.tv_subtitle_ext)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_subtitle_duration)
        val btnUpload: ImageView = itemView.findViewById(R.id.btn_upload_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subtitle_option, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isSelected = item.id == selectedId

        holder.tvName.text = item.simpleName.ifEmpty { item.name }
        holder.tvExt.text = item.ext.uppercase(Locale.US)

        // 来源样式
        holder.tvSource.text = item.sourceType.label
        if (item.sourceType == SubtitleSourceType.ONE_ONE_FIVE) {
            holder.tvSource.setBackgroundColor("#33FF9800".toColorInt())
            holder.tvSource.setTextColor("#FF9800".toColorInt())
            // 已在 115 目录的字幕无需再上传
            holder.btnUpload.visibility = View.GONE
        } else {
            holder.tvSource.setBackgroundColor("#3342A5F5".toColorInt())
            holder.tvSource.setTextColor("#42A5F5".toColorInt())
            holder.btnUpload.visibility = View.VISIBLE
            holder.btnUpload.setOnClickListener {
                onUploadClick?.invoke(item)
            }
        }

        // 时长文本（通过 SubtitleItem.durationMs 转换）
        if (item.durationMs > 0) {
            val totalSec = item.durationMs / 1000
            val sec = totalSec % 60
            val min = (totalSec / 60) % 60
            val hour = totalSec / 3600
            holder.tvDuration.visibility = View.VISIBLE
            val timeStr = if (hour > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hour, min, sec)
            } else {
                String.format(Locale.US, "%02d:%02d", min, sec)
            }
            holder.tvDuration.text = "时长: $timeStr"
        } else {
            holder.tvDuration.visibility = View.GONE
        }

        // 选中高亮状态
        if (isSelected) {
            holder.cardSubtitle.setCardBackgroundColor("#3342A5F5".toColorInt())
            holder.tvName.setTypeface(null, Typeface.BOLD)
            holder.tvName.setTextColor("#42A5F5".toColorInt())
        } else {
            holder.cardSubtitle.setCardBackgroundColor("#1AFFFFFF".toColorInt())
            holder.tvName.setTypeface(null, Typeface.NORMAL)
            holder.tvName.setTextColor("#D0FFFFFF".toColorInt())
        }

        holder.itemView.setOnClickListener {
            if (selectedId != item.id) {
                selectedId = item.id
                notifyDataSetChanged()
            }
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SubtitleItem>, newSelectedId: String = selectedId) {
        items = newItems
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    fun updateSelectedId(newSelectedId: String) {
        if (selectedId != newSelectedId) {
            selectedId = newSelectedId
            notifyDataSetChanged()
        }
    }
}
