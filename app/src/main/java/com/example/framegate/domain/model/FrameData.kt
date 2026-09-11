package com.example.framegate.domain.model

data class FrameData(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val pixelStride: Int,
    val sensorRotation: Int,
    val isMirrored: Boolean,
    val yBuffer: ByteArray,
    val timestampEpochMs: Long
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameData
        if (width != other.width) return false
        if (height != other.height) return false
        if (rowStride != other.rowStride) return false
        if (pixelStride != other.pixelStride) return false
        if (sensorRotation != other.sensorRotation) return false
        if (isMirrored != other.isMirrored) return false
        if (!yBuffer.contentEquals(other.yBuffer)) return false
        if (timestampEpochMs != other.timestampEpochMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rowStride
        result = 31 * result + pixelStride
        result = 31 * result + sensorRotation
        result = 31 * result + isMirrored.hashCode()
        result = 31 * result + yBuffer.contentHashCode()
        result = 31 * result + timestampEpochMs.hashCode()
        return result
    }
}

