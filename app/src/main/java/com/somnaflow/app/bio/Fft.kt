package com.somnaflow.app.bio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 FFT plus a helper that returns a Hann-windowed power spectrum. */
class Fft(val n: Int) {
    init { require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" } }
    private val cosT = FloatArray(n / 2) { cos(2 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2 * PI * it / n).toFloat() }
    private val hann = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val re = FloatArray(n); private val im = FloatArray(n)

    fun transform(re: FloatArray, im: FloatArray) {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2; val step = n / len
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val c = cosT[k * step]; val s = -sinT[k * step]
                    val xr = re[i + k + half] * c - im[i + k + half] * s
                    val xi = re[i + k + half] * s + im[i + k + half] * c
                    re[i + k + half] = re[i + k] - xr; im[i + k + half] = im[i + k] - xi
                    re[i + k] += xr; im[i + k] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Power spectrum (n/2 bins) of the last n samples of [x] starting at [offset], mean removed, Hann windowed. */
    fun power(x: FloatArray, offset: Int, out: FloatArray) {
        var mean = 0f
        for (i in 0 until n) mean += x[offset + i]
        mean /= n
        for (i in 0 until n) { re[i] = (x[offset + i] - mean) * hann[i]; im[i] = 0f }
        transform(re, im)
        for (i in 0 until n / 2) out[i] = (re[i] * re[i] + im[i] * im[i]) / n
    }
}
