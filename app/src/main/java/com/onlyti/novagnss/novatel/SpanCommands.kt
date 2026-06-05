package com.onlyti.novagnss.novatel

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/** A vehicle-frame direction an IMU axis can point. Vehicle frame: X=right, Y=forward, Z=up. */
enum class Direction(val label: String, val vx: Double, val vy: Double, val vz: Double) {
    FORWARD("forward", 0.0, 1.0, 0.0),
    BACKWARD("backward", 0.0, -1.0, 0.0),
    RIGHT("right", 1.0, 0.0, 0.0),
    LEFT("left", -1.0, 0.0, 0.0),
    UP("up", 0.0, 0.0, 1.0),
    DOWN("down", 0.0, 0.0, -1.0),
}

/** Builders for NovAtel SPAN configuration/calibration commands. Verified against OEM7 docs. */
object SpanCommands {

    private fun fmt(v: Double): String =
        String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

    /**
     * Compute SETINSROTATION RBV from the IMU body axes' directions in the vehicle frame.
     * Returns the command string, or null if the picks don't form a valid right-handed rotation.
     * Rotation matrix R (body->vehicle) columns are the images of body X/Y/Z; we extract Euler
     * angles in NovAtel's Z-X-Y order (right-handed): R = Ry(Y)*Rx(X)*Rz(Z).
     */
    fun rbvFromAxes(imuX: Direction, imuY: Direction, imuZ: Direction): String? {
        // R[row][col]; column c = image of body axis c.
        val r = arrayOf(
            doubleArrayOf(imuX.vx, imuY.vx, imuZ.vx),
            doubleArrayOf(imuX.vy, imuY.vy, imuZ.vy),
            doubleArrayOf(imuX.vz, imuY.vz, imuZ.vz),
        )
        if (!isRightHandedRotation(r)) return null
        val xRot = Math.toDegrees(asin(-r[1][2]))
        val zRot = Math.toDegrees(atan2(r[1][0], r[1][1]))
        val yRot = Math.toDegrees(atan2(r[0][2], r[2][2]))
        return setInsRotationRbv(xRot, yRot, zRot)
    }

    private fun isRightHandedRotation(r: Array<DoubleArray>): Boolean {
        // columns orthonormal + det = +1
        fun dot(a: Int, b: Int) = (0..2).sumOf { r[it][a] * r[it][b] }
        if (abs(dot(0, 0) - 1) > 1e-6 || abs(dot(1, 1) - 1) > 1e-6 || abs(dot(2, 2) - 1) > 1e-6) return false
        if (abs(dot(0, 1)) > 1e-6 || abs(dot(0, 2)) > 1e-6 || abs(dot(1, 2)) > 1e-6) return false
        val det = r[0][0] * (r[1][1] * r[2][2] - r[1][2] * r[2][1]) -
            r[0][1] * (r[1][0] * r[2][2] - r[1][2] * r[2][0]) +
            r[0][2] * (r[1][0] * r[2][1] - r[1][1] * r[2][0])
        return abs(det - 1.0) < 1e-6
    }

    fun setInsRotationRbv(xDeg: Double, yDeg: Double, zDeg: Double, sd: Double = 1.0): String =
        "SETINSROTATION RBV ${fmt(xDeg)} ${fmt(yDeg)} ${fmt(zDeg)} ${fmt(sd)} ${fmt(sd)} ${fmt(sd)}"

    /** Antenna 1 lever arm: IMU nav-centre -> primary antenna phase centre. Frame IMUBODY/VEHICLE. */
    fun setAnt1Translation(x: Double, y: Double, z: Double, sd: Double = 0.05, frame: String = "IMUBODY"): String =
        "SETINSTRANSLATION ANT1 ${fmt(x)} ${fmt(y)} ${fmt(z)} ${fmt(sd)} ${fmt(sd)} ${fmt(sd)} $frame"

    /** USER output point: relocates where the INS solution is reported (default enclosure = 0,0,0). */
    fun setUserTranslation(x: Double, y: Double, z: Double, frame: String = "IMUBODY"): String =
        "SETINSTRANSLATION USER ${fmt(x)} ${fmt(y)} ${fmt(z)} $frame"

    fun saveConfig(): String = "SAVECONFIG"

    // RBV kinematic calibration (INSCALIBRATE). Requires a seed RBV + alignment to INS_SOLUTION_GOOD,
    // then drive >5 m/s on straight level courses. Monitor via INSCALSTATUS.
    fun calibrateRbvNew(sdDeg: Double = 0.5): String = "INSCALIBRATE RBV NEW ${fmt(sdDeg)}"
    fun calibrateRbvAdd(sdDeg: Double = 0.5): String = "INSCALIBRATE RBV ADD ${fmt(sdDeg)}"
    fun calibrateRbvStop(): String = "INSCALIBRATE RBV STOP"
    fun calibrateRbvReset(): String = "INSCALIBRATE RBV RESET"
    fun logInsCalStatus(): String = "LOG INSCALSTATUSA ONCHANGED"

    /** Status-tab subscriptions (1 Hz / onchanged). Sent when entering the Status tab. */
    val STATUS_LOGS = listOf(
        "LOG INSPVAXA ONTIME 1",
        "LOG BESTPOSA ONTIME 1",
        "LOG SATVIS2A ONTIME 1",
    )
    val STATUS_LOGS_STOP = listOf("UNLOG INSPVAXA", "UNLOG BESTPOSA", "UNLOG SATVIS2A")
}
