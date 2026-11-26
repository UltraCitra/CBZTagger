package com.cbztagger.app.core

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * ComicInfo.xml parser and serializer following the standard ComicInfo schema.
 * Preserves unknown XML child elements from existing ComicInfo.xml files when updating tags.
 */
object ComicInfoXml {
    const val FILENAME = "ComicInfo.xml"

    private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
    private const val XSD_NS = "http://www.w3.org/2001/XMLSchema"

    fun isValid(xmlBytes: ByteArray): Boolean = parse(xmlBytes) != null

    fun parse(xmlBytes: ByteArray): ComicInfo? {
        if (xmlBytes.isEmpty()) return null
        return try {
            val cleaned = stripUtf8Bom(xmlBytes)
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                try {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                } catch (_: Exception) {
                    // Some Android XML parsers do not support disallow-doctype-decl
                }
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(cleaned))
            val root = doc.documentElement ?: return null
            if (root.tagName != "ComicInfo") return null

            fun text(tag: String): String = findDirectChild(root, tag)?.textContent?.trim().orEmpty()
            fun intVal(tag: String): Int? = text(tag).toIntOrNull()
            fun floatVal(tag: String): Float? = text(tag).toFloatOrNull()

            val bwRaw = text("BlackAndWhite")
            val bw = when {
                bwRaw.isBlank() -> null
                bwRaw.equals("yes", true) || bwRaw.equals("true", true) || bwRaw == "1" -> true
                bwRaw.equals("no", true) || bwRaw.equals("false", true) || bwRaw == "0" -> false
                else -> null
            }

            val pages = mutableListOf<PageInfo>()
            val pagesElem = findDirectChild(root, "Pages")
            if (pagesElem != null) {
                val children = pagesElem.childNodes
                var idx = 0
                for (i in 0 until children.length) {
                    val node = children.item(i)
                    if (node is Element && node.tagName == "Page") {
                        val imageIdx = node.getAttribute("Image").toIntOrNull() ?: idx
                        val type = PageType.fromXml(node.getAttribute("Type"))
                        val doublePageAttr = node.getAttribute("DoublePage")
                        val doublePage = when {
                            doublePageAttr.isBlank() -> null
                            doublePageAttr.equals("true", true) ||
                                doublePageAttr.equals("yes", true) ||
                                doublePageAttr == "1" -> true
                            doublePageAttr.equals("false", true) ||
                                doublePageAttr.equals("no", true) ||
                                doublePageAttr == "0" -> false
                            else -> null
                        }
                        pages += PageInfo(
                            image = imageIdx,
                            type = type,
                            doublePage = doublePage,
                            imageSize = node.getAttribute("ImageSize").toLongOrNull(),
                            imageWidth = node.getAttribute("ImageWidth").toIntOrNull(),
                            imageHeight = node.getAttribute("ImageHeight").toIntOrNull(),
                            bookmark = node.getAttribute("Bookmark").orEmpty(),
                        )
                        idx++
                    }
                }
            }

            ComicInfo(
                series = text("Series"),
                number = text("Number"),
                count = intVal("Count"),
                title = text("Title"),
                volume = intVal("Volume"),
                alternateSeries = text("AlternateSeries"),
                alternateNumber = text("AlternateNumber"),
                alternateCount = intVal("AlternateCount"),
                summary = text("Summary"),
                notes = text("Notes"),
                year = intVal("Year"),
                month = intVal("Month"),
                day = intVal("Day"),
                writer = text("Writer"),
                penciller = text("Penciller"),
                inker = text("Inker"),
                colorist = text("Colorist"),
                letterer = text("Letterer"),
                coverArtist = text("CoverArtist"),
                editor = text("Editor"),
                publisher = text("Publisher"),
                imprint = text("Imprint"),
                genre = text("Genre"),
                web = text("Web"),
                pageCount = intVal("PageCount"),
                languageIso = text("LanguageISO"),
                format = text("Format"),
                blackAndWhite = bw,
                manga = text("Manga"),
                characters = text("Characters"),
                teams = text("Teams"),
                locations = text("Locations"),
                scanInformation = text("ScanInformation"),
                storyArc = text("StoryArc"),
                seriesGroup = text("SeriesGroup"),
                ageRating = text("AgeRating"),
                communityRating = floatVal("CommunityRating"),
                pages = pages,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serialize(info: ComicInfo, existingXml: ByteArray? = null): ByteArray {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val builder = factory.newDocumentBuilder()
        val doc: Document = if (existingXml != null && existingXml.isNotEmpty()) {
            try {
                val parsed = builder.parse(ByteArrayInputStream(stripUtf8Bom(existingXml)))
                if (parsed.documentElement?.tagName == "ComicInfo") parsed else createBlankDoc(builder)
            } catch (_: Exception) {
                createBlankDoc(builder)
            }
        } else {
            createBlankDoc(builder)
        }

        val root = doc.documentElement
        if (!root.hasAttribute("xmlns:xsi")) root.setAttribute("xmlns:xsi", XSI_NS)
        if (!root.hasAttribute("xmlns:xsd")) root.setAttribute("xmlns:xsd", XSD_NS)

        fun assign(tag: String, value: Any?) {
            val str = value?.toString()?.trim().orEmpty()
            val existing = findDirectChild(root, tag)
            if (str.isNotEmpty()) {
                if (existing != null) {
                    existing.textContent = str
                } else {
                    val elem = doc.createElement(tag)
                    elem.textContent = str
                    root.appendChild(elem)
                }
            } else if (existing != null) {
                root.removeChild(existing)
            }
        }

        assign("Series", info.series)
        assign("Number", info.number)
        assign("Count", info.count)
        assign("Title", info.title)
        assign("Volume", info.volume)
        assign("Genre", info.genre)
        assign("Summary", info.summary)
        assign("Notes", info.notes)
        assign("AlternateSeries", info.alternateSeries)
        assign("AlternateNumber", info.alternateNumber)
        assign("AlternateCount", info.alternateCount)
        assign("StoryArc", info.storyArc)
        assign("SeriesGroup", info.seriesGroup)
        assign("Publisher", info.publisher)
        assign("Imprint", info.imprint)
        assign("Day", info.day)
        assign("Month", info.month)
        assign("Year", info.year)
        assign("LanguageISO", info.languageIso)
        assign("Web", info.web)
        assign("Format", info.format)
        assign("Manga", info.manga)
        assign("BlackAndWhite", if (info.blackAndWhite == true) "Yes" else null)
        assign("AgeRating", info.ageRating)
        assign("CommunityRating", info.communityRating)
        assign("ScanInformation", info.scanInformation)
        assign("PageCount", info.pageCount)
        assign("Characters", info.characters)
        assign("Teams", info.teams)
        assign("Locations", info.locations)
        assign("Writer", info.writer)
        assign("Penciller", info.penciller)
        assign("Inker", info.inker)
        assign("Colorist", info.colorist)
        assign("Letterer", info.letterer)
        assign("CoverArtist", info.coverArtist)
        assign("Editor", info.editor)

        val existingPages = findDirectChild(root, "Pages")
        if (existingPages != null) {
            root.removeChild(existingPages)
        }
        if (info.pages.isNotEmpty()) {
            val pagesElem = doc.createElement("Pages")
            info.pages.sortedBy { it.image }.forEach { page ->
                val pageElem = doc.createElement("Page")
                pageElem.setAttribute("Image", page.image.toString())
                if (page.bookmark.isNotBlank()) pageElem.setAttribute("Bookmark", page.bookmark)
                if (page.doublePage != null) {
                    pageElem.setAttribute("DoublePage", if (page.doublePage) "True" else "False")
                }
                if (page.imageHeight != null) pageElem.setAttribute("ImageHeight", page.imageHeight.toString())
                if (page.imageSize != null) pageElem.setAttribute("ImageSize", page.imageSize.toString())
                if (page.imageWidth != null) pageElem.setAttribute("ImageWidth", page.imageWidth.toString())
                if (page.type.isNotBlank()) pageElem.setAttribute("Type", page.type)
                pagesElem.appendChild(pageElem)
            }
            root.appendChild(pagesElem)
        }

        // Clean whitespace-only text nodes so pretty indentation stays clean
        removeWhitespaceNodes(root)

        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            try {
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            } catch (_: Exception) {
                // Ignore if transformer does not support indent-amount
            }
        }
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    private fun createBlankDoc(builder: javax.xml.parsers.DocumentBuilder): Document {
        val doc = builder.newDocument()
        val root = doc.createElement("ComicInfo")
        root.setAttribute("xmlns:xsi", XSI_NS)
        root.setAttribute("xmlns:xsd", XSD_NS)
        doc.appendChild(root)
        return doc
    }

    private fun findDirectChild(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == tagName) {
                return node
            }
        }
        return null
    }

    private fun removeWhitespaceNodes(element: Element) {
        val children = element.childNodes
        for (i in children.length - 1 downTo 0) {
            val node = children.item(i)
            when (node.nodeType) {
                Node.TEXT_NODE -> {
                    if (node.textContent.isNullOrBlank() && element.childNodes.length > 1) {
                        element.removeChild(node)
                    }
                }
                Node.ELEMENT_NODE -> removeWhitespaceNodes(node as Element)
            }
        }
    }

    private fun stripUtf8Bom(bytes: ByteArray): ByteArray {
        return if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
    }
}
