package com.rork.hollowmarch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rork.hollowmarch.game.SaveStore
import com.rork.hollowmarch.game.Sprite
import com.rork.hollowmarch.game.Sprites
import com.rork.hollowmarch.ui.navigation.AppNavigation
import com.rork.hollowmarch.ui.theme.AppTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SaveStore.init(this)
        installCreatureArt()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }

    /**
     * The province's dead, painted: each sheet holds four facings — the face,
     * the nose right, the nose left, the back — keyed off its black ground and
     * brought down to the boards the renderer draws.
     */
    private fun installCreatureArt() {
        Sprites.ensureBuilt()
        install(Sprites.HUSK, R.drawable.creature_husk, 72)
        install(Sprites.HOUND, R.drawable.creature_hound, 44)
        install(Sprites.WRAITH, R.drawable.creature_wraith, 76)
    }

    private fun install(id: Int, resId: Int, targetH: Int) {
        val views = facings(resId, targetH)
        Sprites.installCreatureViews(id, views[0], views[1], views[2], views[3])
    }

    /** One sheet's four cells, keyed, cropped to the figure, and scaled to the board. */
    private fun facings(resId: Int, targetH: Int): Array<Sprite> {
        val src = BitmapFactory.decodeResource(resources, resId)
        val cw = src.width / 2
        val ch = src.height / 2
        // sheet order: the face, the nose right, the nose left, the back
        val origins = listOf(0 to 0, cw to 0, 0 to ch, cw to ch)
        return Array(4) { i ->
            val (ox, oy) = origins[i]
            val cell = IntArray(cw * ch)
            src.getPixels(cell, 0, cw, ox, oy, cw, ch)
            // the black ground goes transparent, and the figure's own bounds found
            var minX = cw
            var minY = ch
            var maxX = 0
            var maxY = 0
            for (p in cell.indices) {
                val c = cell[p]
                val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (lum < 30) {
                    cell[p] = 0
                } else {
                    cell[p] = (c and 0x00FFFFFF) or (0xFE shl 24)
                    val x = p % cw
                    val y = p / cw
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < minX || maxY < minY) {
                minX = 0
                minY = 0
                maxX = cw - 1
                maxY = ch - 1
            }
            val w = maxX - minX + 1
            val h = maxY - minY + 1
            val cropped = IntArray(w * h)
            for (y in 0 until h) {
                System.arraycopy(cell, (y + minY) * cw + minX, cropped, y * w, w)
            }
            val outW = ((w.toFloat() * targetH / h).roundToInt()).coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(
                Bitmap.createBitmap(cropped, w, h, Bitmap.Config.ARGB_8888),
                outW, targetH, true
            )
            val px = IntArray(outW * targetH)
            scaled.getPixels(px, 0, outW, 0, 0, outW, targetH)
            // the scaler softens the edges back toward the black: key the fringe once more
            for (p in px.indices) {
                val c = px[p]
                val lum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
                if (lum < 30) px[p] = 0
            }
            Sprite(outW, targetH, px)
        }
    }
}
