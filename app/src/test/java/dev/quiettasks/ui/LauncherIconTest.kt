package dev.quiettasks.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    @Test fun launcherUsesReferenceSymbolAndThemeResources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (dark in listOf(false, true)) {
            val config = Configuration(app.resources.configuration).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            val context = app.createConfigurationContext(config)
            val image = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val drawable = context.getDrawable(R.drawable.ic_launcher)!!
            drawable.setBounds(0, 0, 512, 512); drawable.draw(Canvas(image))
            assertEquals(Color.parseColor(if (dark) "#28211E" else "#F4EDE2"), image.getPixel(10, 256))
            assertEquals(Color.parseColor("#4CA987"), image.getPixel(285, 171))
            assertEquals(Color.parseColor(if (dark) "#FFFFFF" else "#28211E"), image.getPixel(256, 248))
            assertTrue(context.getDrawable(R.mipmap.ic_launcher) is android.graphics.drawable.AdaptiveIconDrawable)
            File("build/previews/logo-${if (dark) "dark" else "light"}.png").apply { parentFile!!.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
