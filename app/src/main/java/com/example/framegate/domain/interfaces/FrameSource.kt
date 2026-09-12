package com.example.framegate.domain.interfaces

import com.example.framegate.domain.model.FrameData

interface FrameSource {
    fun getNextFrame(): FrameData?
}
