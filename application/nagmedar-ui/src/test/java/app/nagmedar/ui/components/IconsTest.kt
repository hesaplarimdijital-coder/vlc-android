package app.nagmedar.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** S-203: the icon set is complete, drawn on its grid, and only directional icons mirror. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconsTest {

    @Test
    fun everyIconIsListedOnceByItsName() {
        val declared = NagIcons::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == ImageVector::class.java }
            .map { it.name.removePrefix("get") }
            .toSet()
        val listed = NagIcons.all.map { it.name }
        assertEquals(listed.size, listed.toSet().size)
        assertEquals(declared, listed.toSet())
    }

    @Test
    fun iconsStayInsideTheirGrid() {
        val outside = mutableListOf<String>()
        NagIcons.all.forEach { icon ->
            assertEquals(icon.name, 16f, icon.viewportWidth)
            assertEquals(icon.name, 16f, icon.viewportHeight)
            icon.root.forEach { node ->
                val path = node as VectorPath
                val bounds = PathParser().addPathNodes(path.pathData).toPath().getBounds()
                // a stroke reaches half its width beyond the path
                val half = if (path.stroke != null) path.strokeLineWidth / 2 else 0f
                if (bounds.left - half < -0.01f || bounds.top - half < -0.01f || bounds.right + half > 16.01f || bounds.bottom + half > 16.01f) {
                    outside += "${icon.name} $bounds"
                }
                if (path.stroke != null) assertEquals(icon.name, 1.6f, path.strokeLineWidth)
            }
        }
        assertTrue(outside.joinToString("\n"), outside.isEmpty())
    }

    @Test
    fun onlyDirectionalIconsMirror() {
        assertEquals(setOf("ChevronLeft", "ChevronRight"), NagIcons.all.filter { it.autoMirror }.map { it.name }.toSet())
    }
}
