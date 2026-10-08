package com.darusc.mousedroid.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D-looking Interactive Holographic Dot Surface for Mousedroid Trackpad.
 *
 * - The dots belong to a fixed, evenly spaced grid representing a virtual 3D surface.
 * - Touching and moving across the trackpad creates a localized 3D depression / wave
 *   that physically deforms the digital surface in real-time.
 * - The grid itself stays anchored to the screen; the 3D deformation follows the finger.
 * - Perspective displacement: dots shift radially and vertically based on 3D depth (Z).
 * - Variable dot size, brightness, and specular cyan highlights on raised crests.
 * - Darker, discrete dots in recessed areas with zero blob merging.
 * - Physical spring-damper inertia when moving, smooth harmonic settling when stopped.
 * - Elastic relaxation back to the resting flat plane when lifted.
 * - Zero memory allocations in draw/update loop for 60/120fps hardware acceleration.
 */
class ParticleTouchOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class DeformationSource {
        var id = -1
        var active = false

        // Instantaneous finger target
        var targetX = 0f
        var targetY = 0f

        // Spring-smoothed deformation center
        var centerX = 0f
        var centerY = 0f

        // Inertia velocity
        var vx = 0f
        var vy = 0f

        // Deformation amplitude (0.0 to 1.0)
        var intensity = 0f

        // Harmonic phase clock for ambient waves when stationary
        var phase = 0f
    }

    companion object {
        private const val MAX_SOURCES = 2
    }

    private val density = resources.displayMetrics.density
    private val gridSpacing = 17.5f * density
    private val influenceRadius = 78f * density
    private val maxDepthZ = 24f * density

    private val sources = Array(MAX_SOURCES) { DeformationSource() }

    // Pre-allocated grid dot coordinates
    private var gridCols = 0
    private var gridRows = 0
    private var totalDots = 0
    private var gridBaseX = FloatArray(0)
    private var gridBaseY = FloatArray(0)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var isAnimating = false
    private var lastFrameTime = 0L

    init {
        isFocusable = false
        isClickable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        // Compute grid columns and rows to symmetrically tile the surface
        gridCols = (w / gridSpacing).toInt() + 1
        gridRows = (h / gridSpacing).toInt() + 1
        totalDots = gridCols * gridRows

        gridBaseX = FloatArray(totalDots)
        gridBaseY = FloatArray(totalDots)

        // Symmetrical margins so dots are evenly centered
        val offsetX = (w - (gridCols - 1) * gridSpacing) * 0.5f
        val offsetY = (h - (gridRows - 1) * gridSpacing) * 0.5f

        var idx = 0
        for (r in 0 until gridRows) {
            val y = offsetY + r * gridSpacing
            for (c in 0 until gridCols) {
                val x = offsetX + c * gridSpacing
                gridBaseX[idx] = x
                gridBaseY[idx] = y
                idx++
            }
        }

        invalidate()
    }

    /**
     * Feed raw MotionEvents from Touchpad without interfering with gesture detection.
     */
    fun onPointerEvent(event: MotionEvent) {
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerId = event.getPointerId(actionIndex)
                val src = findOrCreateSource(pointerId)
                if (src != null) {
                    val px = event.getX(actionIndex)
                    val py = event.getY(actionIndex)

                    src.targetX = px
                    src.targetY = py

                    if (!src.active) {
                        src.centerX = px
                        src.centerY = py
                        src.vx = 0f
                        src.vy = 0f
                        src.intensity = 0.25f
                        src.phase = 0f
                    }
                    src.active = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until min(event.pointerCount, MAX_SOURCES)) {
                    val pointerId = event.getPointerId(i)
                    val src = findSource(pointerId)
                    if (src != null && src.active) {
                        src.targetX = event.getX(i)
                        src.targetY = event.getY(i)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                val src = findSource(pointerId)
                if (src != null) {
                    src.active = false
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                for (src in sources) {
                    src.active = false
                }
            }
        }

        startAnimationIfNeeded()
    }

    private fun findSource(id: Int): DeformationSource? {
        return sources.firstOrNull { it.id == id }
    }

    private fun findOrCreateSource(id: Int): DeformationSource? {
        var src = findSource(id)
        if (src == null) {
            src = sources.firstOrNull { !it.active && it.intensity <= 0.01f }
            if (src != null) {
                src.id = id
            }
        }
        return src
    }

    private fun startAnimationIfNeeded() {
        if (!isAnimating) {
            isAnimating = true
            lastFrameTime = System.nanoTime()
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (totalDots == 0) return

        val now = System.nanoTime()
        val dt = if (lastFrameTime > 0) {
            ((now - lastFrameTime) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
        } else {
            0.016f
        }
        lastFrameTime = now

        var hasActiveDeformation = false

        // 1. Update physics for all active deformation sources
        for (src in sources) {
            if (src.active) {
                // Ramps up smoothly on touch
                src.intensity += (1.0f - src.intensity) * (dt * 15f).coerceAtMost(1f)
                hasActiveDeformation = true
            } else {
                // Smooth elastic relaxation back to flat plane on release
                src.intensity += (0.0f - src.intensity) * (dt * 8f).coerceAtMost(1f)
                if (src.intensity > 0.005f) {
                    hasActiveDeformation = true
                } else {
                    src.id = -1
                    src.intensity = 0f
                }
            }

            if (src.intensity > 0.005f) {
                // Physical Spring-Damper tracking for deformation center (subtle trailing inertia)
                val spring = 22f
                val damping = 0.74f

                val dx = src.targetX - src.centerX
                val dy = src.targetY - src.centerY

                src.vx = src.vx * damping + dx * (spring * dt)
                src.vy = src.vy * damping + dy * (spring * dt)

                src.centerX += src.vx
                src.centerY += src.vy

                // Advance harmonic phase for ambient micro-wave when stationary
                src.phase += dt * 3.5f
            }
        }

        // Base resting dot styling
        val restRadius = 1.35f * density
        val restColor = Color.argb(45, 56, 189, 248) // Subtle ambient holographic slate-cyan

        val inflSq = influenceRadius * influenceRadius

        // 2. Render each dot in the stationary 3D grid
        for (i in 0 until totalDots) {
            val baseX = gridBaseX[i]
            val baseY = gridBaseY[i]

            var totalZ = 0f
            var dispX = 0f
            var dispY = 0f
            var maxIntensity = 0f
            var nearestDistRatio = 1.0f

            // Calculate 3D height and perspective displacement from all deformation sources
            for (src in sources) {
                if (src.intensity <= 0.005f) continue

                val dx = baseX - src.centerX
                val dy = baseY - src.centerY
                val distSq = dx * dx + dy * dy

                if (distSq < inflSq) {
                    val dist = hypot(dx, dy)
                    val u = (dist / influenceRadius).coerceIn(0f, 1f)

                    if (u < nearestDistRatio) {
                        nearestDistRatio = u
                    }
                    if (src.intensity > maxIntensity) {
                        maxIntensity = src.intensity
                    }

                    // 3D Membrane Height Function:
                    // - Deep depression directly under the finger (u = 0, z < 0)
                    // - Smooth raised elastic crest surrounding the finger (u ≈ 0.35..0.65, z > 0)
                    // - Smooth tangential return to flat plane (u → 1.0, z = 0)
                    val baseCurve = (-cos(Math.PI.toFloat() * u * 1.5f) + 0.32f * sin(Math.PI.toFloat() * u * 2.0f))
                    val falloff = (1.0f - u)
                    val curveDamped = baseCurve * falloff * falloff

                    // Subtle breathing micro-wave when resting/holding
                    val breathing = sin(src.phase + u * 4.0f) * 0.08f * falloff

                    val localZ = (curveDamped + breathing) * maxDepthZ * src.intensity
                    totalZ += localZ

                    // Perspective Displacement:
                    // Radial shift: points below plane (Z < 0) pull slightly inward toward touch center;
                    // points on crest (Z > 0) push slightly outward over ridge.
                    val dirX = if (dist > 0.1f) dx / dist else 0f
                    val dirY = if (dist > 0.1f) dy / dist else 0f

                    val radialShift = (localZ * 0.22f)
                    dispX += dirX * radialShift

                    // Perspective elevation tilt (viewing from slightly downward angle):
                    // Raised points shift up, depressed points sink downward
                    val verticalTilt = -localZ * 0.28f
                    dispY += dirY * radialShift + verticalTilt
                }
            }

            if (maxIntensity > 0.005f && totalZ != 0f) {
                // Dot is within an active 3D deformation zone
                val drawX = baseX + dispX
                val drawY = baseY + dispY

                // Variable Dot Size based on 3D depth (closer to eye = larger)
                val zRatio = (totalZ / maxDepthZ).coerceIn(-1f, 1f)
                val dotRadius = if (zRatio >= 0f) {
                    restRadius + (2.2f * density * zRatio * maxIntensity) // Raised crest: larger
                } else {
                    (restRadius + (0.45f * density * zRatio * maxIntensity)).coerceAtLeast(0.9f * density) // Recessed: smaller
                }

                // 3D Depth Lighting & Shading
                // - Recessed areas (zRatio < 0): darker, deep blue tone
                // - Raised crest (zRatio > 0): glowing electric cyan neon with specular highlights
                val r: Int
                val g: Int
                val b: Int
                val alpha: Int

                if (zRatio >= 0f) {
                    // Raised Crest / Highlight
                    // Transition from electric cyan (56, 189, 248) to brilliant ice-cyan (224, 242, 254)
                    val highlight = zRatio.coerceIn(0f, 1f)
                    r = (56 + (224 - 56) * highlight).toInt()
                    g = (189 + (242 - 189) * highlight).toInt()
                    b = (248 + (254 - 248) * highlight).toInt()
                    alpha = (45 + ((245 - 45) * highlight * maxIntensity)).toInt().coerceIn(0, 255)
                } else {
                    // Recessed Depression
                    // Deep oceanic blue shadow (2, 132, 199)
                    val shadow = (-zRatio).coerceIn(0f, 1f)
                    r = (56 + (2 - 56) * shadow).toInt()
                    g = (189 + (132 - 189) * shadow).toInt()
                    b = (248 + (199 - 248) * shadow).toInt()
                    alpha = (45 + ((150 - 45) * shadow * maxIntensity)).toInt().coerceIn(0, 255)
                }

                dotPaint.color = Color.argb(alpha, r, g, b)
                canvas.drawCircle(drawX, drawY, dotRadius, dotPaint)
            } else {
                // Resting baseline dot on the flat plane
                dotPaint.color = restColor
                canvas.drawCircle(baseX, baseY, restRadius, dotPaint)
            }
        }

        // Continue animation loop while deformation is dynamic
        if (hasActiveDeformation) {
            postInvalidateOnAnimation()
        } else {
            isAnimating = false
        }
    }
}
