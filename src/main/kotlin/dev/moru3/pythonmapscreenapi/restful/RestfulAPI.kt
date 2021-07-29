package dev.moru3.pythonmapscreenapi.restful

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dev.moru3.pythonmapscreenapi.Screen
import dev.moru3.pythonmapscreenapi.websocket.WebSocketConfig.Companion.tokens
import dev.moru3.pythonmapscreenapi.websocket.WebSocketHandler.Companion.sessions
import org.bukkit.map.MapPalette
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.socket.WebSocketSession
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest


@Controller
@RequestMapping("/api/rest/rts/screen")
class RestfulAPI {
    val gson = Gson()
    var processingNow = false

    companion object {
        // list1=maps,list2=movies,list3=pixels
        var videoData: MutableMap<String, List<List<List<Byte>>>> = mutableMapOf()
        val videoIdToScreen = mutableMapOf<String, List<Screen>>()
        val screenIdToVideoId = mutableMapOf<String, String>()
    }

    fun checkAuthorization(value: String?): Boolean {
        try {
            requireNotNull(value) { return false }
            val type: String
            val fov: String
            value.split(" ").also { type = it[0];fov = it[1] }
            return when (type) {
                "Bearer" -> {
                    tokens[fov]?.after(Date()) ?: run { tokens.remove(fov);false }
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun checkAuth(header: HttpServletRequest): Boolean {
        return checkAuthorization(header.getHeader("Authorization"))
    }

    @RequestMapping(path = ["/send"], method = [RequestMethod.POST])
    fun send(
        header: HttpServletRequest,
        @RequestParam("video_id", required = true) videoId: String,
        @RequestParam("screen_id", required = true) screenId: String,
        @RequestParam("fps", required = true) fps: Int
    ): ResponseEntity<String> {
        check(checkAuthorization(header.getHeader("Authorization"))) {
            return ResponseEntity(gson.toJson(JsonObject().apply { addProperty("code", 401);addProperty("message", "The Token is incorrect.") }), HttpStatus.UNAUTHORIZED)
        }
        val res = videoData[videoId]
        if(res == null) {
            val file = File("${System.getProperty("user.dir")}\\files\\${videoId}\\frames")
            var data = mutableMapOf<Int, List<Byte>>()
            var blockHeight: Int? = null
            var blockWidth: Int? = null
            val files = file.listFiles()?.filter { it.name.endsWith(".json") }?.sortedBy { it.name }?: return ResponseEntity(gson.toJson(JsonObject().apply { addProperty("code", 400);addProperty("message", "指定されたIDの映像が見つかりませんでした。") }), HttpStatus.BAD_REQUEST)

            files.parallelStream().forEach { file1 ->
                val json = gson.fromJson(String(file1.inputStream().readBytes()), JsonObject::class.java)
                json["blockHeight"]?.asInt?.also { blockHeight = it }
                json["blockWidth"]?.asInt?.also { blockWidth = it }
                data[files.indexOf(file1)] = json["data"].asJsonArray.map(JsonElement::getAsByte)
            }
            data = data.toSortedMap()

            val screens: MutableList<List<List<Byte>>> = mutableListOf()

            println("${blockHeight} : ${blockWidth}")
            if(blockHeight!=null&&blockWidth!=null) {
                (0 until blockHeight!!).toList().forEach {h -> (0 until blockWidth!!).toList().forEach { w ->
                    val videoFrames = mutableListOf<List<Byte>>()
                    data.values.forEach {
                        val result = mutableListOf<Byte>()
                        for (wi in 0 until 128) { for (he in 0 until 128) { result.add(it[w + wi + ((h + he) * blockWidth!! * 128)]) } }
                        videoFrames.add(result)
                    }
                    screens+=videoFrames
                }}
            } else {
                return ResponseEntity(gson.toJson(JsonObject().apply { addProperty("code", 400);addProperty("message", "正常にデータが保存されていません。") }), HttpStatus.BAD_REQUEST)
            }
            videoData[videoId] = screens.toList()
            println("${screens.size} ${screens.first().size} ${screens.first().first().size}")
            return send(header, videoId, screenId, fps)
        } else {
            if(screenIdToVideoId[screenId]!=videoId) {
                screenIdToVideoId[screenId] = videoId
                if(videoIdToScreen[videoId]==null) { Screen(fps, videoId, screenId, res).start() }
            }
            return ResponseEntity(gson.toJson(JsonObject().apply { addProperty("code", 200);addProperty("message", "OK!") }), HttpStatus.OK)
        }
    }

    @RequestMapping(path = ["/screens"], method = [RequestMethod.GET])
    fun getServers(header: HttpServletRequest): ResponseEntity<String> {
        check(checkAuthorization(header.getHeader("Authorization"))) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    401
                );addProperty("message", "The Token is incorrect.")
            }), HttpStatus.UNAUTHORIZED)
        }
        return ResponseEntity(gson.toJson(JsonObject().apply {
            addProperty("code", 200);addProperty(
            "message",
            "OK!"
        );add("list", JsonArray().also { sessions.keys.forEach(it::add) })
        }), HttpStatus.OK)
    }

    @RequestMapping(path = ["/check"], method = [RequestMethod.GET])
    fun check(header: HttpServletRequest): ResponseEntity<String> {
        check(checkAuthorization(header.getHeader("Authorization"))) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    401
                );addProperty("message", "The Token is incorrect.")
            }), HttpStatus.UNAUTHORIZED)
        }

        return ResponseEntity(gson.toJson(JsonObject().apply {
            addProperty("code", 200);addProperty(
            "message",
            "OK!"
        )
        }), HttpStatus.OK)
    }

    @RequestMapping(path = ["/add"], method = [RequestMethod.POST])
    fun addVideo(
        header: HttpServletRequest,
        @RequestParam("file", required = false) file: MultipartFile?,
        @RequestParam("id", required = false) id: String?,
        @RequestParam("command", required = false) command: String?,
        @RequestParam("x", required = false) x: Int?,
        @RequestParam("y", required = false) y: Int?
    ): ResponseEntity<String> {
        val auth = header.getHeader("Authorization")
        check(checkAuthorization(auth)) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    401
                );addProperty("message", "認証に失敗しました。Tokenが間違っている可能性があります。")
            }), HttpStatus.UNAUTHORIZED)
        }
        if (processingNow) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty("code", 503);addProperty(
                "message",
                "現在別の動画を処理中です。終了後にもう一度お試しください。"
            )
            }), HttpStatus.SERVICE_UNAVAILABLE)
        }
        checkNotNull(id) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    400
                );addProperty("message", "IDを指定してください。")
            }), HttpStatus.BAD_REQUEST)
        }
        checkNotNull(x) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    400
                );addProperty("message", "Xを指定してください。")
            }), HttpStatus.BAD_REQUEST)
        }
        checkNotNull(y) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    400
                );addProperty("message", "Yを指定してください。")
            }), HttpStatus.BAD_REQUEST)
        }
        checkNotNull(file) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    400
                );addProperty("message", "fileを指定してください。")
            }), HttpStatus.BAD_REQUEST)
        }
        check(file.originalFilename?.endsWith(".mp4") == true) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty(
                    "code",
                    400
                );addProperty("message", "ファイルがmp4である必要があります。: ${file.name}")
            }), HttpStatus.BAD_REQUEST)
        }
        val file1 = File("${System.getProperty("user.dir")}\\files\\${id}\\video.mp4")
        if (file1.exists()) {
            return ResponseEntity(gson.toJson(JsonObject().apply {
                addProperty("code", 409);addProperty(
                "message",
                "IDが重複しています。"
            )
            }), HttpStatus.CONFLICT)
        }
        file1.parentFile.mkdirs()
        BufferedOutputStream(FileOutputStream(file1)).apply { write(file.bytes) }.also(BufferedOutputStream::flush)
            .also(BufferedOutputStream::close)
        println("${file1.parentFile.absoluteFile} に video.mp4を保存しました。")

        encode(file1, x, y, command)

        file1.parentFile.resolve("frames").mkdirs()
        return ResponseEntity(gson.toJson(JsonObject().apply {
            addProperty("code", 200);addProperty(
            "message",
            "OK!"
        )
        }), HttpStatus.OK)
    }

    fun encode(file: File, x: Int, y: Int, command: String?) {
        processingNow = true
        var end = false
        val endProcess = mutableListOf<Runnable>()
        val ffmpegFrameGrabber = FFmpegFrameGrabber(file).also(FFmpegFrameGrabber::start)
        val frameConverter = Java2DFrameConverter()
        var failCount = 0
        val executor = Executors.newFixedThreadPool(6)
        var first = false
        val size = ffmpegFrameGrabber.lengthInVideoFrames
        for (te in 0 until ffmpegFrameGrabber.lengthInVideoFrames) {
            executor.execute {
                val count = te
                try {
                    if (end) {
                        return@execute
                    }
                    val i = frameConverter.convert(ffmpegFrameGrabber.grabImage() ?: throw NullPointerException("null"))
                        ?: throw NullPointerException("null")
                    val imageHeight = i.height
                    val imageWidth = i.width
                    val mag: Double =
                        ((y * 128.0) / imageHeight).takeIf { imageWidth * it <= x * 128.0 } ?: (x * 128.0) / imageWidth
                    val height = (imageHeight * mag).toInt()
                    val width = (imageWidth * mag).toInt()
                    val marginTop = ((y * 128) - height) / 2
                    val marginLeft = ((x * 128) - width) / 2
                    val temp = BufferedImage(x * 128, y * 128, BufferedImage.TYPE_INT_ARGB).also { image ->
                        image.createGraphics().also { graphics2D ->
                            graphics2D.paint = Color.WHITE
                            graphics2D.fillRect(0, 0, image.width, image.height)
                            graphics2D.drawImage(
                                image.getScaledInstance(width, height, Image.SCALE_DEFAULT),
                                marginLeft,
                                marginTop,
                                null
                            )
                            graphics2D.dispose()
                        }
                    }
                    val data = file.parentFile.resolve("frames").resolve("data-${count}.json")
                    data.createNewFile()
                    if (!first) {
                        val videoData = VideoData(y, x, command ?: "", MapPalette.imageToBytes(temp).toList())
                        data.outputStream().use {
                            it.write(gson.toJson(videoData).toByteArray(Charsets.UTF_8))
                            it.close()
                        }
                        first = true
                    } else {
                        val videoData = MapDataSimply(MapPalette.imageToBytes(temp).toList())
                        data.outputStream().use {
                            it.write(gson.toJson(videoData).toByteArray(Charsets.UTF_8))
                            it.close()
                        }
                    }
                    println("${count + 1}:${ffmpegFrameGrabber.lengthInFrames - 1}")
                    if (count + 1 >= ffmpegFrameGrabber.lengthInFrames - 1) {
                        println("処理終了")
                        endProcess.forEach(Runnable::run)
                        processingNow = false
                    }
                } catch (e: Exception) {
                    if (count < 5) {
                        println("5回未満の時点で失敗しているため再度エンコードします。")
                        end = true
                        endProcess.add {
                            file.parentFile.resolve("frames").delete()
                            encode(file, x, y, command)
                        }
                        return@execute
                    }
                    failCount++
                    endProcess.add {
                        Files.copy(
                            file.parentFile.resolve("frames").resolve("data-${count - 1}.json").toPath(),
                            file.parentFile.resolve("frames").resolve("data-${count}.json").toPath()
                        )
                    }
                    if (failCount <= size / 20) {
                        e.printStackTrace()
                        println("$failCount 回失敗してます: 失敗したため\"data-${count - 1}.json\"をコピーし\"data-${count}.json\"という名前で保存します。")
                    } else {
                        end = true
                        endProcess.add {
                            file.parentFile.resolve("frames").delete()
                            encode(file, x, y, command)
                        }
                        println("fail回数が多すぎるため再度エンコードします。")
                        return@execute
                    }
                }
            }
        }
    }
}