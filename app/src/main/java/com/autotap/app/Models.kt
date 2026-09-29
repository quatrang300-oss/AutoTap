package com.autotap.app

import org.json.JSONArray
import org.json.JSONObject

/** Một điểm chạm: toạ độ màn hình (px) và thời điểm (ms) tính từ lúc bắt đầu nét vẽ. */
data class TouchPoint(val x: Float, val y: Float, val t: Long)

/** Một thao tác (chạm / giữ / vuốt) = một nét, cùng thời gian chờ trước khi thực hiện. */
class Step(val points: List<TouchPoint>, val delayBefore: Long) {

    val duration: Long
        get() = if (points.size < 2) 0L else points.last().t - points.first().t

    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (p in points) {
            arr.put(p.x.toDouble())
            arr.put(p.y.toDouble())
            arr.put(p.t)
        }
        return JSONObject().put("d", delayBefore).put("p", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Step {
            val arr = o.getJSONArray("p")
            val pts = ArrayList<TouchPoint>()
            var i = 0
            while (i + 2 < arr.length()) {
                pts.add(TouchPoint(arr.getDouble(i).toFloat(), arr.getDouble(i + 1).toFloat(), arr.getLong(i + 2)))
                i += 3
            }
            return Step(pts, o.optLong("d", 0L))
        }
    }
}

enum class MacroType {
    /** Thao tác do người dùng ghi lại. */
    RECORDED,

    /** Auto click thông thường theo danh sách điểm. */
    POINTS
}

class Macro(
    val id: String,
    var name: String,
    val type: MacroType,
    val steps: MutableList<Step> = mutableListOf(),
    var speed: Float = 1f,        // hệ số tốc độ phát lại (RECORDED)
    var loops: Int = 1,           // 0 = lặp vô hạn
    var loopDelay: Long = 0L,     // nghỉ giữa các vòng (ms)
    var interval: Long = 300L,    // POINTS: khoảng cách giữa 2 lần click (ms)
    var tapDuration: Long = 40L,  // POINTS: thời gian giữ mỗi click (ms)
    var enabled: Boolean = false, // hiện nút combo trên màn hình
    var btnX: Int = -1,           // vị trí nút combo
    var btnY: Int = -1,
    var keyCode: Int = 0,         // phím vật lý gán cho combo (0 = chưa gán)
    var keyPress: Int = 0         // 0 = nhấn 1 lần, 1 = nhấn đúp, 2 = giữ lâu
) {
    /** Tổng thời lượng một vòng ở tốc độ 1x (ms). */
    fun oneLoopMs(): Long = when (type) {
        MacroType.RECORDED -> steps.sumOf { it.delayBefore + it.duration }
        MacroType.POINTS -> steps.size * (interval + tapDuration)
    }

    fun toJson(): JSONObject {
        val arr = JSONArray()
        steps.forEach { arr.put(it.toJson()) }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("type", type.name)
            .put("speed", speed.toDouble())
            .put("loops", loops)
            .put("loopDelay", loopDelay)
            .put("interval", interval)
            .put("tapDuration", tapDuration)
            .put("enabled", enabled)
            .put("btnX", btnX)
            .put("btnY", btnY)
            .put("keyCode", keyCode)
            .put("keyPress", keyPress)
            .put("steps", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): Macro {
            val type = try {
                MacroType.valueOf(o.optString("type", MacroType.RECORDED.name))
            } catch (e: IllegalArgumentException) {
                MacroType.RECORDED
            }
            val m = Macro(
                id = o.getString("id"),
                name = o.optString("name", "Thao tác"),
                type = type,
                speed = o.optDouble("speed", 1.0).toFloat(),
                loops = o.optInt("loops", 1),
                loopDelay = o.optLong("loopDelay", 0L),
                interval = o.optLong("interval", 300L),
                tapDuration = o.optLong("tapDuration", 40L),
                enabled = o.optBoolean("enabled", false),
                btnX = o.optInt("btnX", -1),
                btnY = o.optInt("btnY", -1),
                keyCode = o.optInt("keyCode", 0),
                keyPress = o.optInt("keyPress", 0)
            )
            val arr = o.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until arr.length()) m.steps.add(Step.fromJson(arr.getJSONObject(i)))
            return m
        }
    }
}

/**
 * Mẫu hình ảnh cần nhận diện: vùng (left, top, width, height) trên màn hình nơi hình ảnh xuất hiện.
 * Hình mẫu lưu ở file PNG riêng (Store.templateFile(id)).
 */
class ImageTrigger(
    val id: String,
    var name: String,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val screenW: Int,
    val screenH: Int,
    var margin: Int = 40,          // sai lệch vị trí cho phép (px) quanh vùng đã chọn
    var threshold: Int = 85,       // độ giống tối thiểu (%)
    var action: String = ACTION_CLICK,
    var cooldown: Long = 1500L,    // thời gian chờ trước khi được kích hoạt lại (ms)
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name)
        .put("left", left).put("top", top).put("width", width).put("height", height)
        .put("screenW", screenW).put("screenH", screenH)
        .put("margin", margin).put("threshold", threshold)
        .put("action", action).put("cooldown", cooldown).put("enabled", enabled)

    companion object {
        const val ACTION_CLICK = "__click__"

        fun fromJson(o: JSONObject) = ImageTrigger(
            id = o.getString("id"),
            name = o.optString("name", "Hình"),
            left = o.getInt("left"), top = o.getInt("top"),
            width = o.getInt("width"), height = o.getInt("height"),
            screenW = o.getInt("screenW"), screenH = o.getInt("screenH"),
            margin = o.optInt("margin", 40),
            threshold = o.optInt("threshold", 85),
            action = o.optString("action", ACTION_CLICK),
            cooldown = o.optLong("cooldown", 1500L),
            enabled = o.optBoolean("enabled", true)
        )
    }
}
