package com.a02.draw.feature.home.screen.home

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.imageview.ShapeableImageView

class SquareTopicImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ShapeableImageView(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val side = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> measuredWidth
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        setMeasuredDimension(side, side)
    }
}
