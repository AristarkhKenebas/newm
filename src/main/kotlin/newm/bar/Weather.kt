package newm.bar

/** The address asked for the weather: [template] with %s the place (encoded; "" lets the service guess it by the address the question comes from). */
internal fun weatherUrl(template: String, place: String): String =
    template.replace("%s", java.net.URLEncoder.encode(place.trim(), Charsets.UTF_8).replace("+", "%20"))

/** What the bar shows of the service's answer: its first line, short; null for a page or an error. */
internal fun cleanWeather(body: String): String? {
    val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    if ('<' in line || line.startsWith("Unknown", ignoreCase = true) || line.contains("error", ignoreCase = true)) return null
    return line.replace(Regex("\\s+"), " ").take(40)
}

/** Asks [url] (off the compositor's thread): the weather's text, or null. */
internal fun fetchWeather(url: String): String? {
    val client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()
    val request = java.net.http.HttpRequest.newBuilder(java.net.URI(url)).timeout(java.time.Duration.ofSeconds(15))
        // (wttr.in answers a terminal's tools with plain text.)
        .header("User-Agent", "curl/8").GET().build()
    val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
    return if (response.statusCode() == 200) cleanWeather(response.body()) else null
}
