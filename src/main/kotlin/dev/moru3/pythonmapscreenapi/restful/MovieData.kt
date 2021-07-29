package dev.moru3.pythonmapscreenapi.restful

data class VideoData(val blockHeight: Int, val blockWidth: Int, val command: String, val data: List<Byte>)