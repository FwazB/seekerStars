package com.portalritual.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sensor-based ARRuntime using TYPE_ROTATION_VECTOR.
 * Extracts device yaw via rotation matrix -> orientation angles.
 * Uses YawSmoother for EMA + dead-zone filtering.
 */
class SensorRuntime(context: Context) : ARRuntime, SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val yawSmoother = YawSmoother()
    private val pitchSmoother = YawSmoother()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    @Volatile private var _yawDeg: Float = 0f
    @Volatile private var _pitchDeg: Float = 0f

    // Parallax baseline — captured when constellation starts
    @Volatile private var baselineYaw: Float = Float.NaN
    @Volatile private var baselinePitch: Float = Float.NaN
    private val parallaxStrength = 0.03f
    private val parallaxClamp = 0.05f

    override val currentYawDeg: Float get() = _yawDeg

    override fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        yawSmoother.reset()
        pitchSmoother.reset()
    }

    override fun resetBaseline() {
        baselineYaw = _yawDeg
        baselinePitch = _pitchDeg
    }

    override fun parallaxOffset(): Pair<Float, Float> {
        if (baselineYaw.isNaN()) return Pair(0f, 0f)

        // Shortest angular distance for yaw (handles ±180° wraparound)
        var dyaw = _yawDeg - baselineYaw
        if (dyaw > 180f) dyaw -= 360f
        if (dyaw < -180f) dyaw += 360f

        val dpitch = _pitchDeg - baselinePitch

        // Negate: tilt right → stars shift left (parallax window effect)
        val dx = (-dyaw * parallaxStrength).coerceIn(-parallaxClamp, parallaxClamp)
        val dy = (-dpitch * parallaxStrength).coerceIn(-parallaxClamp, parallaxClamp)

        return Pair(dx, dy)
    }

    override fun placementPose(distanceMeters: Float): Pose3 {
        val yawRad = Math.toRadians(_yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(_pitchDeg.toDouble()).toFloat()

        // Forward vector from yaw + pitch
        val fx = -sin(yawRad) * cos(pitchRad)
        val fy = -sin(pitchRad)
        val fz = -cos(yawRad) * cos(pitchRad)

        // Quaternion: Y-axis rotation by yaw
        val halfYaw = yawRad / 2f
        return Pose3(
            tx = fx * distanceMeters,
            ty = fy * distanceMeters,
            tz = fz * distanceMeters,
            qx = 0f,
            qy = sin(halfYaw),
            qz = 0f,
            qw = cos(halfYaw)
        )
    }

    // -- SensorEventListener ---------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // [0] = azimuth (yaw), [1] = pitch, [2] = roll  (radians)
        val rawYawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        _yawDeg = yawSmoother.smooth(rawYawDeg)
        _pitchDeg = pitchSmoother.smooth(
            Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }
}
