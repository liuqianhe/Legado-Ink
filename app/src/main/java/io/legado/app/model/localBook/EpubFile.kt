package io.legado.app.model.localBook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.TextUtils
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.BookHelp
import io.legado.app.utils.FileUtils
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.epub.EpubReader
import org.jsoup.Jsoup
import splitties.init.appCtx
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*
import java.util.zip.ZipFile

class EpubFile(var book: Book) {

    companion object {
        private var eFile: EpubFile? = null

        @Synchronized
        private fun getEFile(book: Book): EpubFile {
            BookHelp.getEpubFile(book)

            if (eFile == null || eFile?.book?.bookUrl != book.bookUrl) {
                eFile = EpubFile(book)
                //对于Epub文件默认不启用替换
                book.setUseReplaceRule(false)
                return eFile!!
            }
            eFile?.book = book
            return eFile!!
        }

        @Synchronized
        fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getEFile(book).getChapterList()
        }

        @Synchronized
        fun getContent(book: Book, chapter: BookChapter): String? {
            return getEFile(book).getContent(chapter)
        }

        @Synchronized
        fun getImage(
            book: Book,
            href: String
        ): InputStream? {
            return getEFile(book).getImage(href)
        }

        @Synchronized
        fun upBookInfo(book: Book) {
            return getEFile(book).upBookInfo()
        }
    }

    private var mCharset: Charset = Charset.defaultCharset()
    private var epubBook: EpubBook? = null
        get() {
            if (field != null) {
                return field
            }
            field = readEpub()
            return field
        }

    /**
     * 每章在 epubBook.contents(spine 顺序)中的资源区间。
     * 多看精排 epub 把每章拆成「标题页 chpaterN.xhtml」+「正文页 chpaterN_0001.xhtml」，
     * NCX 目录只收录标题页，原逻辑只读单个资源 → 只剩标题。
     * 这里按 spine 顺序把标题页到下一章之前的全部资源都归入本章，正文就不会丢。
     * key = 章节 href（去掉 fragment），value = contents 中的 [start, endExclusive)。
     */
    private var chapterContentRange: Map<String, IntArray> = emptyMap()

    init {
        try {
            epubBook?.let {
                if (book.coverUrl.isNullOrEmpty()) {
                    book.coverUrl = FileUtils.getPath(
                        appCtx.externalFiles,
                        "covers",
                        "${MD5Utils.md5Encode16(book.bookUrl)}.jpg"
                    )
                }
                if (!File(book.coverUrl!!).exists()) {
                    /*部分书籍DRM处理后，封面获取异常，待优化*/
                    it.coverImage?.inputStream?.use { input ->
                        val cover = BitmapFactory.decodeStream(input)
                        val out = FileOutputStream(FileUtils.createFileIfNotExist(book.coverUrl!!))
                        cover.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.flush()
                        out.close()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /*重写epub文件解析代码，直接读出压缩包文件生成Resources给epublib，这样的好处是可以逐一修改某些文件的格式错误*/
    private fun readEpub(): EpubBook? {
        try {

            val file = BookHelp.getEpubFile(book)
            //通过懒加载读取epub
            return EpubReader().readEpubLazy(ZipFile(file), "utf-8")


        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }

    private fun getContent(chapter: BookChapter): String? {
        /*获取当前章节文本*/
        return getChildChapter(chapter, chapter.url)
    }

    private fun getChildChapter(chapter: BookChapter, href: String): String? {
        val epub = epubBook ?: return null
        try {
            // 阅读流程不经过 getChapterList()，chapterContentRange 可能还没建立（进程重启、
            // 读过别的书导致 eFile 单例重建等）。此时若直接 fallback 只读 href 单个资源，
            // 多看精排书（标题页与正文页分离）就只剩标题。这里按需补建区间映射。
            if (chapterContentRange.isEmpty()) buildChapterContentRangeFromToc()

            // 取本章对应的 spine 资源列表：默认只有 href 本身；
            // 若建立了章节区间（多看精排等一章多文件），则拼接标题页到正文页的全部资源
            val resources = mutableListOf<me.ag2s.epublib.domain.Resource>()
            val plainHref = href.substringBefore("#")
            val range = chapterContentRange[plainHref]
            if (range != null) {
                val contents = epub.contents
                val start = range[0].coerceAtLeast(0)
                val end = range[1].coerceAtMost(contents.size)
                for (i in start until end) {
                    resources.add(contents[i])
                }
            }
            if (resources.isEmpty()) {
                epub.resources.getByHref(href)?.let { resources.add(it) }
            }
            if (resources.isEmpty()) return null

            // 依次解析每个资源，收集其 body 顶层节点
            val bodyElements = mutableListOf<org.jsoup.nodes.Element>()
            resources.forEachIndexed { idx, res ->
                val doc = Jsoup.parse(String(res.data, mCharset))
                val body = doc.body()
                // fragment 截取只针对章节起始资源（原逻辑，兼容一文件多章的书籍）
                if (idx == 0 && chapter.url == href) {
                    val startFragmentId = chapter.startFragmentId
                    val endFragmentId = chapter.endFragmentId
                    if (!startFragmentId.isNullOrBlank())
                        body.getElementById(startFragmentId)?.previousElementSiblings()?.remove()
                    if (!endFragmentId.isNullOrBlank() && endFragmentId != startFragmentId)
                        body.getElementById(endFragmentId)?.nextElementSiblings()?.remove()
                }
                // 多看精排标题页(div.zjy 里的 p.djz/p.zjm)与阅读器章节标题重复，去掉
                if (idx == 0 && isDuokanSplitChapter(plainHref)) {
                    body.select("div.zjy").remove()
                    body.select("p.djz, p.zjm").remove()
                }
                bodyElements.addAll(body.children())
            }

            // 合并到一个容器 body（避免直接构造 jsoup.Elements 的兼容问题）
            val mergedBody = Jsoup.parse("").body()
            bodyElements.forEach { mergedBody.appendChild(it.clone()) }

            mergedBody.select("script").remove()
            mergedBody.select("style").remove()

            /*选择去除正文中的H标签，部分书籍标题与阅读标题重复待优化*/
            var tag = Book.hTag
            if (book.getDelTag(tag)) {
                mergedBody.select("h1, h2, h3, h4, h5, h6").remove()
            }

            /*选择去除正文中的img标签，目前图片支持效果待优化*/
            tag = Book.imgTag
            if (book.getDelTag(tag)) {
                mergedBody.select("img").remove()
            }

            var html = mergedBody.html()
            /*选择去除正文中的ruby标签，目前注释支持效果待优化*/
            tag = Book.rubyTag
            if (book.getDelTag(tag)) {
                html = html.replace("<ruby>\\s?([\\u4e00-\\u9fa5])\\s?.*?</ruby>".toRegex(), "$1")
            }
            return HtmlFormatter.formatKeepImg(html)
        } catch (e: Exception) {
            Timber.e(e)
        }
        return null
    }

    /** 是否为多看精排拆分出来的「标题页」资源（chpaterN.xhtml，另有 chpaterN_0001.xhtml 正文） */
    private fun isDuokanSplitChapter(href: String): Boolean {
        val name = href.substringAfterLast("/")
        // chpater1.xhtml / chapter1.xhtml 等纯数字结尾（无 _0001 后缀），且同包内存在 _0001 正文页
        val base = Regex("""chp?ater\d+\.xhtml$""")
        return base.containsMatchIn(name) &&
            epubBook?.resources?.getByHref(href.substringBeforeLast(".") + "_0001.xhtml") != null
    }

    private fun getImage(href: String): InputStream? {
        val abHref = href.replace("../", "")
        return epubBook?.resources?.getByHref(abHref)?.inputStream
    }

    private fun upBookInfo() {
        if (epubBook == null) {
            eFile = null
            book.intro = "书籍导入异常"
        } else {
            val metadata = epubBook!!.metadata
            book.name = metadata.firstTitle
            if (book.name.isEmpty()) {
                book.name = book.originName.replace(".epub", "")
            }

            if (metadata.authors.size > 0) {
                val author =
                    metadata.authors[0].toString().replace("^, |, $".toRegex(), "")
                book.author = author
            }
            if (metadata.descriptions.size > 0) {
                book.intro = Jsoup.parse(metadata.descriptions[0]).text()
            }
        }
    }

    private fun getChapterList(): ArrayList<BookChapter> {
        val chapterList = ArrayList<BookChapter>()
        epubBook?.tableOfContents?.allUniqueResources?.forEachIndexed { index, resource ->
            var title = resource.title
            if (TextUtils.isEmpty(title)) {
                try {
                    val doc =
                        Jsoup.parse(String(resource.data, mCharset))
                    val elements = doc.getElementsByTag("title")
                    if (elements.size > 0) {
                        title = elements[0].text()
                    }
                } catch (e: IOException) {
                    Timber.e(e)
                }
            }
            val chapter = BookChapter()
            chapter.index = index
            chapter.bookUrl = book.bookUrl
            chapter.url = resource.href
            if (index == 0 && title.isEmpty()) {
                chapter.title = "封面"
            } else {
                chapter.title = title
            }
            chapterList.add(chapter)
        }
        // 建立章节 → spine 资源区间映射，解决多看精排「一章拆多文件」正文丢失问题
        buildChapterContentRange(chapterList)
        book.latestChapterTitle = chapterList.lastOrNull()?.title
        book.totalChapterNum = chapterList.size
        return chapterList
    }

    /**
     * 对每个目录章节，找到它在 spine(contents) 中的资源位置，
     * 区间取到「下一目录章节的资源位置」之前（不含）。
     * 这样夹在标题页与下一章之间的正文页（如 chpaterN_0001.xhtml）会被并入本章。
     */
    private fun buildChapterContentRange(chapterList: List<BookChapter>) {
        val hrefs = chapterList.map { it.url }
        buildRangeFromHrefs(hrefs)
    }

    /**
     * 阅读时按需补建区间映射：直接从 epub 的 TOC 资源取 href，不依赖 chapterList
     * （阅读流程只读数据库的目录，不会调 getChapterList）。
     */
    private fun buildChapterContentRangeFromToc() {
        val hrefs = epubBook?.tableOfContents?.allUniqueResources?.map { it.href } ?: return
        buildRangeFromHrefs(hrefs)
    }

    private fun buildRangeFromHrefs(hrefs: List<String>) {
        val epub = epubBook ?: return
        val contents = epub.contents
        if (contents.isEmpty() || hrefs.isEmpty()) return
        val map = HashMap<String, IntArray>()
        // 目录章节 href（去 fragment）→ 在 contents 中首次出现的索引
        val chapterPositions = hrefs.map { h0 ->
            val h = h0.substringBefore("#")
            h to contents.indexOfFirst { (it.href ?: "").substringBefore("#") == h }
        }
        chapterPositions.forEachIndexed { i, (href, startIdx) ->
            if (startIdx < 0) return@forEachIndexed
            // 终点 = 下一个能定位到的目录章节起点；最后一个目录项取到 spine 末尾
            var endIdx = contents.size
            for (j in (i + 1) until chapterPositions.size) {
                val nextIdx = chapterPositions[j].second
                if (nextIdx > startIdx) {
                    endIdx = nextIdx
                    break
                }
            }
            map[href] = intArrayOf(startIdx, endIdx)
        }
        chapterContentRange = map
    }

}