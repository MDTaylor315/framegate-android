package com.example.framegate.domain.model

data class Rect<T : Number>(
    val left: T,
    val top: T,
    val right: T,
    val bottom: T
)
typealias BufferRect = Rect<Int>
typealias ViewRect = Rect<Float>

val BufferRect.width: Int get() = right - left
val BufferRect.height: Int get() = bottom - top
val ViewRect.width: Float get() = right - left
val ViewRect.height: Float get() = bottom - top

data class CoordinateMapping(
    val bufferRect: BufferRect,
    val viewRect: ViewRect
)