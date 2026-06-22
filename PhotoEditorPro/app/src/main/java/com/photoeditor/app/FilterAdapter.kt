package com.photoeditor.app

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FilterAdapter(
    private val presets: List<FilterPreset>,
    private val thumbnail: Bitmap,
    private val onSelect: (FilterPreset) -> Unit
) : RecyclerView.Adapter<FilterAdapter.VH>() {

    private var selected = 0

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.filterImage)
        val name: TextView = view.findViewById(R.id.filterName)
        val container: LinearLayout = view.findViewById(R.id.filterContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_filter, parent, false))

    override fun getItemCount() = presets.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = presets[position]
        holder.name.text = preset.name
        holder.image.setImageBitmap(
            AdjustmentEngine.applyAdjustments(thumbnail, preset.state)
        )
        holder.container.setBackgroundColor(
            if (position == selected) Color.parseColor("#6200EE") else Color.TRANSPARENT
        )
        holder.itemView.setOnClickListener {
            val prev = selected
            selected = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selected)
            onSelect(preset)
        }
    }
}
