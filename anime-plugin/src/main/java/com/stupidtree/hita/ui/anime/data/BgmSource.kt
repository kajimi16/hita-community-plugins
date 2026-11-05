package com.stupidtree.hita.ui.anime.data


/**
 * BGM 数据读取层：优先走自建缓存服务器，失败再直连 `api.bgm.tv`。
 *
 * - 缓存服务器就是 hit-ani 的 `/api/bgm/...` 代理（落库缓存 + 陈旧回退）
 * - 没配服务器、服务器不可达、或代理路径 404 时自动回退直连
 * - 收藏 / 进度 / 令牌校验仍然只走直连（需要用户自己的 BGM 身份）
 *
 * 方法都是阻塞的，调用方已经在 IO 线程（与旧 [BgmApi] 一致）。
 */
class BgmSource(
    private val direct: BgmApi,
    bases: List<String>,
) {
    private val proxies: List<BgmApi> = bases
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
        .map { base ->
            BgmApi(
                tokenProvider = { null },
                baseUrl = "$base/api/bgm",
                connectTimeoutMs = 4_000,
                readTimeoutMs = 12_000,
            )
        }

    private fun <T> read(block: (BgmApi) -> T): T {
        var lastError: Throwable? = null
        for (proxy in proxies) {
            try {
                return block(proxy)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        try {
            return block(direct)
        } catch (error: Throwable) {
            if (lastError != null) error.addSuppressed(lastError)
            throw error
        }
    }

    fun calendar(): List<CalendarDay> = read { it.calendar() }

    fun airingSchedule(weekOffset: Int = 0): Schedule =
        buildAiringSchedule(calendar(), weekOffset, weekDates(weekOffset))

    fun search(filter: SearchFilter, offset: Int = 0, limit: Int = 20): SearchPage =
        read { it.search(filter, offset, limit) }

    fun subject(id: Int): SubjectDetail = read { it.subject(id) }

    fun episodes(subjectId: Int): List<EpisodeItem> = read { it.episodes(subjectId) }

    fun characters(subjectId: Int): List<CharacterItem> = read { it.characters(subjectId) }

    fun persons(subjectId: Int): List<StaffItem> = read { it.persons(subjectId) }

    fun related(subjectId: Int): List<RelatedSubject> = read { it.related(subjectId) }

    /* ---- 需要用户身份，始终直连 ---- */

    fun me(): BgmUser = direct.me()

    fun userCollection(username: String, subjectId: Int): MyCollection? =
        direct.userCollection(username, subjectId)

    fun userCollections(username: String, type: Int, offset: Int, limit: Int = 30): CollectionPage =
        direct.userCollections(username, type, offset, limit)

    fun watchedEpisodeIds(subjectId: Int): Set<Int> = direct.watchedEpisodeIds(subjectId)

    fun saveCollection(subjectId: Int, type: Int? = null, rate: Int? = null, comment: String? = null) =
        direct.saveCollection(subjectId, type, rate, comment)

    fun setEpisodeType(episodeId: Int, type: Int) = direct.setEpisodeType(episodeId, type)
}
