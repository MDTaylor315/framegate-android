package com.example.framegate.domain.queue

import com.example.framegate.domain.model.Metrics

object ManifestBuilder{
    fun buildManifestJson(
        frameId: String,
        planName: String,
        timestampIso: String,
        metrics: Metrics
    ): String {
        return """
        {
          "frame_id": "$frameId",
          "plan_name": "$planName",
          "timestamp": "$timestampIso",
          "metrics": {
            "mean_luma": ${metrics.meanLuma},
            "std_dev": ${metrics.stdDev},
            "rms": ${metrics.rms}
          }
        }
        """.trimIndent()
    }
}