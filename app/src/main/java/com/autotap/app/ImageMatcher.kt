package com.autotap.app

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Dữ liệu hình mẫu đã thu nhỏ & chuẩn hoá để so khớp nhanh. */
class TemplateData(
    val scale: Float,
    val w: Int,
    val h: Int,
    val centered: FloatArray,
    val raw: FloatArray,
    val mean: Float,
    val norm: Double
) {
    /** Hình gần như một màu → dùng sai khác tuyệt đối thay cho tương quan. */
    val flat: Boolean get() = norm / sqrt(raw.size.toDouble()) < 4.0
}

/**
 * So khớp mẫu bằng tương quan chéo chuẩn hoá (NCC) trên ảnh màu RGB đã thu nhỏ,
 * chỉ tìm trong vùng người dùng đã chọn ± sai lệch cho phép → nhanh và ít báo nhầm.
 */
object ImageMatcher {
    private const val TARGET = 36f

    class Result(val score: Float, val cx: Float, val cy: Float)

    fun prepare(bmp: Bitmap): TemplateData {
        val scale = min(1f, TARGET / max(bmp.width, bmp.height))
        val s = scaleBmp(bmp, scale)
        val raw = rgb(s)
        val w = s.width
        val h = s.height
        if (s !== bmp) s.recycle()
        var sum = 0.0
        for (v in raw) sum += v
        val mean = (sum / raw.size).toFloat()
        val c = FloatArray(raw.size)
        var sq = 0.0
        for (i in raw.indices) {
            val d = raw[i] - mean
            c[i] = d
            sq += d.toDouble() * d
        }
        return TemplateData(scale, w, h, c, raw, mean, sqrt(sq))
    }

    private fun scaleBmp(b: Bitmap, scale: Float): Bitmap {
        if (scale >= 0.999f) return b
        val w = max(1, (b.width * scale).roundToInt())
        val h = max(1, (b.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(b, w, h, true)
    }

    private fun rgb(b: Bitmap): FloatArray {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val out = FloatArray(w * h * 3)
        var k = 0
        for (p in px) {
            out[k++] = ((p shr 16) and 0xFF).toFloat()
            out[k++] = ((p shr 8) and 0xFF).toFloat()
            out[k++] = (p and 0xFF).toFloat()
        }
        return out
    }

    /** @return điểm giống nhất (0..1) và tâm vị trí tìm thấy (toạ độ màn hình), hoặc null nếu không so được. */
    fun match(screen: Bitmap, t: ImageTrigger, td: TemplateData): Result? {
        if (screen.width != t.screenW || screen.height != t.screenH) return null // đã xoay màn hình
        val m = max(0, t.margin)
        val l = max(0, t.left - m)
        val tp = max(0, t.top - m)
        val r = min(screen.width, t.left + t.width + m)
        val b = min(screen.height, t.top + t.height + m)
        if (r - l < t.width || b - tp < t.height) return null

        val crop = Bitmap.createBitmap(screen, l, tp, r - l, b - tp)
        val reg = scaleBmp(crop, td.scale)
        val rw = reg.width
        val rh = reg.height
        val img = rgb(reg)
        if (reg !== crop) reg.recycle()
        if (crop !== screen) crop.recycle()

        val tw = td.w
        val th = td.h
        if (rw < tw || rh < th) return null
        val n = tw * th * 3
        val rowLen = tw * 3
        val tc = td.centered
        val traw = td.raw

        var best = -2f
        var bx = 0
        var by = 0
        outer@ for (oy in 0..rh - th) {
            for (ox in 0..rw - tw) {
                var score: Float
                if (td.flat) {
                    var diff = 0.0
                    var k = 0
                    for (y in 0 until th) {
                        var idx = ((oy + y) * rw + ox) * 3
                        for (x in 0 until rowLen) {
                            diff += abs(img[idx] - traw[k])
                            idx++; k++
                        }
                    }
                    score = (1.0 - (diff / n) / 40.0).toFloat()
                } else {
                    var sI = 0.0
                    var sI2 = 0.0
                    var sTI = 0.0
                    var k = 0
                    for (y in 0 until th) {
                        var idx = ((oy + y) * rw + ox) * 3
                        for (x in 0 until rowLen) {
                            val v = img[idx].toDouble()
                            sI += v
                            sI2 += v * v
                            sTI += tc[k] * v
                            idx++; k++
                        }
                    }
                    val varI = sI2 - sI * sI / n
                    score = if (varI <= 1.0) 0f else (sTI / (td.norm * sqrt(varI))).toFloat()
                    // NCC bỏ qua độ sáng tổng thể → phạt nếu độ sáng trung bình khác hẳn.
                    if (abs(sI / n - td.mean) > 45.0) score *= 0.6f
                }
                if (score > best) {
                    best = score; bx = ox; by = oy
                    if (best > 0.985f) break@outer
                }
            }
        }
        val cx = l + (bx + tw / 2f) / td.scale
        val cy = tp + (by + th / 2f) / td.scale
        return Result(best, cx, cy)
    }
}
