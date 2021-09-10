package com.hsu.mapapp.map

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class MapListDeco(private val size: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top += size
        } else {
            outRect.bottom += size
        }

    }
}