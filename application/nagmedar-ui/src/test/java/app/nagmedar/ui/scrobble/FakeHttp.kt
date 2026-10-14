package app.nagmedar.ui.scrobble

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/** An HTTP client that never leaves the process: it answers [answer] and keeps the requests it got. */
class FakeHttp(var answer: (Request) -> Result<Pair<Int, String>> = { Result.success(200 to "{}") }) {
    val requests = mutableListOf<Request>()
    val bodies = mutableListOf<String>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
                val (code, text) = answer(request).getOrElse { throw it as? IOException ?: IOException(it) }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fake")
                    .body(text.toResponseBody("application/json".toMediaType()))
                    .build()
            },
        )
        .build()

    /** The form fields of the [index]th request, decoded. */
    fun form(index: Int = 0): Map<String, String> = bodies[index].split('&').filter { it.isNotEmpty() }.associate {
        val (name, value) = it.split('=', limit = 2)
        java.net.URLDecoder.decode(name, "UTF-8") to java.net.URLDecoder.decode(value, "UTF-8")
    }
}
