package com.epochdefenders.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Sensor-based AR runtime for parallax effect.
 * Uses TYPE_ROTATION_VECTOR to extract yaw + pitch,
 * smooths via YawSmoother, and outputs pixel offsets.
 */
class SensorRuntime(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val yawSmoother = YawSmoother()
    private val pitchSmoother = YawSmoother()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    @Volatile private var yawDeg: Float = 0f
    @Volatile private var pitchDeg: Float = 0f
    @Volatile private var baselineYaw: Float = Float.NaN
    @Volatile private var baselinePitch: Float = Float.NaN

    private val pxPerDegree = 2f
    private val maxOffsetPx = 30f

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        resetBaseline()
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        yawSmoother.reset()
        pitchSmoother.reset()
        baselineYaw = Float.NaN
        baselinePitch = Float.NaN
    }

    fun resetBaseline() {
        baselineYaw = yawDeg
        baselinePitch = pitchDeg
    }

    /**
     * Returns parallax offset in pixels (dx, dy).
     * Tilting right → grid shifts left (window parallax effect).
     * Range: ±[maxOffsetPx] (default ±30px).
     */
    fun parallaxOffset(): Pair<Float, Float> {
        if (baselineYaw.isNaN()) return Pair(0f, 0f)

        var dyaw = yawDeg - baselineYaw
        if (dyaw > 180f) dyaw -= 360f
        if (dyaw < -180f) dyaw += 360f
        val dpitch = pitchDeg - baselinePitch

        val dx = (-dyaw * pxPerDegree).coerceIn(-maxOffsetPx, maxOffsetPx)
        val dy = (-dpitch * pxPerDegree).coerceIn(-maxOffsetPx, maxOffsetPx)
        return Pair(dx, dy)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        yawDeg = yawSmoother.smooth(
            Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        )
        pitchDeg = pitchSmoother.smooth(
            Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
