package com.emir.gym.assistant

/**
 * Gecmisi saglayiciya gondermeden once saf bicimde hazirlar:
 *  - yarida kalmis arac cagrilarina sonuc eklenir (saglayicilar sonucsuz tool_use'u reddeder),
 *  - sahipsiz arac sonuclari atilir, bos mesajlar atilir,
 *  - ardisik ayni rol mesajlari birlestirilir (arac sonuclari basa),
 *  - cok uzayan gecmis son [MAX_MESSAGES] mesaja kirpilir,
 *  - ham Anthropic icerigi yalniz ayni saglayici+modelden geldiyse korunur.
 */
object History {
    const val MAX_MESSAGES = 60

    fun prepare(all: List<ChatMessage>, provider: ProviderId, model: String): List<ChatMessage> {
        val repaired = merge(repair(all.filter { it.blocks.isNotEmpty() }))
        val windowed = window(repaired)
        val trimmed = windowed.size < repaired.size
        val lastUserText = windowed.indexOfLast { m -> m.role == Role.USER && m.blocks.any { it is Block.Text || it is Block.Image } }
        return windowed.mapIndexed { i, m ->
            val sameSource = m.provider == provider.name && m.model == model
            // Kirpma onceki on-eki degistirdigi icin eski turlarin ham (thinking'li) icerigi birakilir.
            val blockIds = m.blocks.filterIsInstance<Block.ToolUse>().map { it.id }.toSet()
            // Ham icerikteki tool_use'lar blok listesiyle (ve onarilan tool_result'larla) eslesmiyorsa ham icerik kullanilmaz.
            val keepRaw = m.rawAnthropic != null && sameSource && (!trimmed || i > lastUserText) &&
                AnthropicWire.toolUseIds(m.rawAnthropic) == blockIds
            if (m.rawAnthropic != null && !keepRaw) m.copy(rawAnthropic = null) else m
        }
    }

    fun repair(msgs: List<ChatMessage>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        var i = 0
        while (i < msgs.size) {
            val m = msgs[i]
            if (m.role == Role.ASSISTANT) {
                out += m
                val uses = m.blocks.filterIsInstance<Block.ToolUse>()
                if (uses.isNotEmpty()) {
                    val next = msgs.getOrNull(i + 1)?.takeIf { it.role == Role.USER }
                    val have = next?.blocks?.filterIsInstance<Block.ToolResult>()?.map { it.id }?.toSet().orEmpty()
                    val missing = uses.filter { it.id !in have }.map {
                        Block.ToolResult(it.id, "Hata: işlem yarıda kaldı, sonuç alınamadı.", true)
                    }
                    val useIds = uses.map { it.id }.toSet()
                    if (next != null) {
                        // Yalniz bu cagrilara ait sonuclar kalir; eksikler eklenir.
                        val kept = next.blocks.filter { it !is Block.ToolResult || it.id in useIds }
                        out += next.copy(blocks = missing + kept)
                        i += 2
                        continue
                    } else if (missing.isNotEmpty()) {
                        out += ChatMessage(Role.USER, missing, provider = m.provider, model = m.model)
                    }
                }
            } else {
                // Onceki assistant'ta karsiligi olmayan sonuclar atilir.
                val prevUseIds = (out.lastOrNull()?.takeIf { it.role == Role.ASSISTANT })
                    ?.blocks?.filterIsInstance<Block.ToolUse>()?.map { it.id }?.toSet().orEmpty()
                val blocks = m.blocks.filter { it !is Block.ToolResult || it.id in prevUseIds }
                if (blocks.isNotEmpty()) out += m.copy(blocks = blocks)
            }
            i++
        }
        return out
    }

    fun merge(msgs: List<ChatMessage>): List<ChatMessage> {
        val out = ArrayList<ChatMessage>()
        for (m in msgs) {
            val last = out.lastOrNull()
            if (last != null && last.role == m.role && m.role == Role.USER) {
                val all = last.blocks + m.blocks
                val results = all.filterIsInstance<Block.ToolResult>()
                out[out.lastIndex] = last.copy(blocks = results + all.filter { it !is Block.ToolResult })
            } else out += m
        }
        return out
    }

    /** Son [MAX_MESSAGES] mesaj; ilk mesaj bir kullanici metni olacak sekilde (arac dongusu ortasindan baslamaz). */
    fun window(msgs: List<ChatMessage>): List<ChatMessage> {
        if (msgs.size <= MAX_MESSAGES) return msgs
        var start = msgs.size - MAX_MESSAGES
        while (start < msgs.size && !(msgs[start].role == Role.USER && msgs[start].blocks.none { it is Block.ToolResult })) start++
        return if (start >= msgs.size) msgs.takeLast(MAX_MESSAGES) else msgs.subList(start, msgs.size)
    }
}
