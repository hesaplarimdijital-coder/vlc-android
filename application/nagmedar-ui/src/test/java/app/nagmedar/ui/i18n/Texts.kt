package app.nagmedar.ui.i18n

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The texts of the new interface as the user reads them (S-1501): this module's strings and the
 * ones Nağmedar added to the classic app's resources ("nagmedar_…"), in English and in Turkish,
 * by key: "nag_x", "nag_y#one" for a plural's quantity, "nag_z[2]" for an array's item. Texts
 * that are not to be translated are left out. Tests run in the module's directory.
 */
internal object Texts {
    val english: Map<String, String> by lazy {
        read("src/main/res/values/strings.xml") + read("../resources/src/main/res/values/strings.xml", prefix = "nagmedar_")
    }

    val turkish: Map<String, String> by lazy {
        read("src/main/res/values-tr/strings.xml") + read("../resources/src/main/res/values-tr/strings.xml", prefix = "nagmedar_")
    }

    fun read(path: String, prefix: String? = null): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
        val texts = LinkedHashMap<String, String>()
        val nodes = root.childNodes
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            if ((prefix != null && !name.startsWith(prefix)) || element.getAttribute("translatable") == "false") continue
            when (element.tagName) {
                "string" -> texts[name] = shown(element.textContent)
                "plurals", "string-array" -> {
                    val items = element.getElementsByTagName("item")
                    for (item in 0 until items.length) {
                        val node = items.item(item) as Element
                        val suffix = if (element.tagName == "plurals") "#" + node.getAttribute("quantity") else "[$item]"
                        texts[name + suffix] = shown(node.textContent)
                    }
                }
            }
        }
        return texts
    }

    /** A resource's text as aapt reads it: \' \" \\ \n \t \uXXXX are escapes, a bare " is dropped. */
    fun shown(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'u' -> {
                        out.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                        i += 4
                    }
                    else -> out.append(next)
                }
                i += 2
            } else {
                if (c != '"') out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
