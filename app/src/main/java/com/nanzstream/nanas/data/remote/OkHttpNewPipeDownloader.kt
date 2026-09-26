package com.nanzstream.nanas.data.remote

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())

        request.headers()?.forEach { (key, values) ->
            values.forEach { builder.addHeader(key, it) }
        }

        val data = request.dataToSend()
        val body = if (data != null) data.toRequestBody(null) else null
        builder.method(request.httpMethod(), body)

        val response = client.newCall(builder.build()).execute()
        val responseBody = response.body?.string() ?: ""

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            response.request.url.toString()
        )
    }
}
