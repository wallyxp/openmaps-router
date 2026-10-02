package com.openmapsrouter.app.parser

import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lon: Double)

object S2Geometry {

    private fun rotateAndFlipQuadrant(n: Double, point: DoubleArray, rx: Double, ry: Double) {
        if (ry == 0.0) {
            if (rx == 1.0) {
                point[0] = n - 1.0 - point[0]
                point[1] = n - 1.0 - point[1]
            }
            val x = point[0]
            point[0] = point[1]
            point[1] = x
        }
    }

    private fun singleSTtoUV(st: Double): Double {
        return if (st >= 0.5) {
            (1.0 / 3.0) * (4.0 * st * st - 1.0)
        } else {
            (1.0 / 3.0) * (1.0 - (4.0 * (1.0 - st) * (1.0 - st)))
        }
    }

    private fun faceUVToXYZ(face: Int, u: Double, v: Double): DoubleArray {
        return when (face) {
            0 -> doubleArrayOf(1.0, u, v)
            1 -> doubleArrayOf(-u, 1.0, v)
            2 -> doubleArrayOf(-u, -v, 1.0)
            3 -> doubleArrayOf(-1.0, -v, -u)
            4 -> doubleArrayOf(v, -1.0, -u)
            5 -> doubleArrayOf(v, u, -1.0)
            else -> throw IllegalArgumentException("Invalid face: $face")
        }
    }

    fun decodeCellIdHex(hexRaw: String): LatLng? {
        val hex = hexRaw.removePrefix("0x").removePrefix("0X")
        if (hex.length < 8) return null

        val cellId = try {
            hex.toULong(16)
        } catch (e: Exception) {
            return null
        }

        // 64-bit binary representation padded with leading zeros
        val bin = cellId.toString(2).padStart(64, '0')
        val face = bin.substring(0, 3).toInt(2)
        val lsbIndex = bin.lastIndexOf('1')
        if (lsbIndex <= 3) return null

        val posB = bin.substring(3, lsbIndex)
        val level = posB.length / 2

        // Hilbert quad digits
        val quads = IntArray(level)
        for (i in 0 until level) {
            quads[i] = posB.substring(i * 2, i * 2 + 2).toInt(2)
        }

        val point = doubleArrayOf(0.0, 0.0)
        for (i in level - 1 downTo 0) {
            val bit = quads[i]
            var rx = 0.0
            var ry = 0.0
            if (bit == 1) {
                ry = 1.0
            } else if (bit == 2) {
                rx = 1.0
                ry = 1.0
            } else if (bit == 3) {
                rx = 1.0
            }
            val valPow = 2.0.pow((level - i - 1).toDouble())
            rotateAndFlipQuadrant(valPow, point, rx, ry)
            point[0] += valPow * rx
            point[1] += valPow * ry
        }

        if (face % 2 == 1) {
            val t = point[0]
            point[0] = point[1]
            point[1] = t
        }

        val maxSize = 2.0.pow(level.toDouble())
        val st0 = (point[0] + 0.5) / maxSize
        val st1 = (point[1] + 0.5) / maxSize

        val u = singleSTtoUV(st0)
        val v = singleSTtoUV(st1)

        val xyz = faceUVToXYZ(face, u, v)
        val lat = atan2(xyz[2], sqrt(xyz[0] * xyz[0] + xyz[1] * xyz[1])) * 180.0 / Math.PI
        val lon = atan2(xyz[1], xyz[0]) * 180.0 / Math.PI

        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            return null
        }

        return LatLng(lat, lon)
    }
}
