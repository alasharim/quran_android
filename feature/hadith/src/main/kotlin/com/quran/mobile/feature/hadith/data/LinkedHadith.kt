package com.quran.mobile.feature.hadith.data

import com.quran.data.model.SuraAyah

enum class HadithCollection(val slug: String) {
  BUKHARI("bukhari"),
  MUSLIM("muslim");

  companion object {
    fun fromSlug(slug: String): HadithCollection? = entries.firstOrNull { it.slug == slug }
  }
}

data class LinkedHadith(
  val id: Long,
  val collection: HadithCollection,
  val number: String,
  val chapter: String?,
  val text: String,
  val grade: String,
  val isAboutWholeSura: Boolean
) {
  /** sunnah.com shows the hadith with its English translation. */
  val sunnahUrl: String
    get() = "https://sunnah.com/${collection.slug}:$number"
}

/**
 * The ayat that have hadiths about them specifically, and the surahs that have hadiths
 * about the surah as a whole.
 */
data class HadithLinks(
  private val ayahs: Set<SuraAyah>,
  private val suras: Set<Int>
) {
  /** Whether hadiths are linked to this ayah itself, which is what the indicators show. */
  fun hasAyahHadiths(suraAyah: SuraAyah): Boolean = suraAyah in ayahs

  /** Whether there is anything to show for this ayah, counting hadiths about its surah. */
  fun hasHadiths(suraAyah: SuraAyah): Boolean = suraAyah in ayahs || suraAyah.sura in suras

  companion object {
    val EMPTY = HadithLinks(emptySet(), emptySet())
  }
}
