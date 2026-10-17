package com.quran.mobile.feature.hadith.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@SingleIn(AppScope::class)
class HadithRepository @Inject constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranFileManager: QuranFileManager
) {
  private val mutex = Mutex()
  private var database: HadithDatabase? = null
  private var links: HadithLinks? = null

  suspend fun links(): HadithLinks {
    return withContext(Dispatchers.IO) {
      mutex.withLock {
        links ?: loadLinks()?.also { links = it } ?: HadithLinks.EMPTY
      }
    }
  }

  suspend fun hadithsForAyah(suraAyah: SuraAyah): List<LinkedHadith> {
    return withContext(Dispatchers.IO) {
      mutex.withLock {
        val queries = openDatabase()?.hadithQueries ?: return@withLock emptyList()
        queries.hadithsForAyah(suraAyah.sura.toLong(), suraAyah.ayah.toLong())
          .executeAsList()
          .mapNotNull { row ->
            val collection = HadithCollection.fromSlug(row.collection) ?: return@mapNotNull null
            LinkedHadith(
              id = row.id,
              collection = collection,
              number = row.number,
              chapter = row.chapter,
              text = row.text,
              grade = row.grade,
              isAboutWholeSura = row.ayah == 0L
            )
          }
      }
    }
  }

  private fun loadLinks(): HadithLinks? {
    val rows = openDatabase()?.hadithQueries?.linkedAyahs()?.executeAsList() ?: return null
    return HadithLinks(
      ayahs = rows.filter { it.ayah > 0 }.map { SuraAyah(it.sura.toInt(), it.ayah.toInt()) }.toSet(),
      suras = rows.filter { it.ayah == 0L }.map { it.sura.toInt() }.toSet()
    )
  }

  private fun openDatabase(): HadithDatabase? {
    database?.let { return it }

    val directory = quranFileManager.databaseDirectory()
    val file = File(directory, DATABASE_NAME)
    if (!file.exists() && !copyFromAssets(directory, file)) {
      return null
    }

    val driver = AndroidSqliteDriver(HadithDatabase.Schema, appContext, name = file.absolutePath)
    return HadithDatabase(driver).also { database = it }
  }

  private fun copyFromAssets(directory: File, file: File): Boolean {
    // copy to a temporary name first so an interrupted copy is never mistaken for the database
    val temporaryName = "$DATABASE_NAME.tmp"
    val copied = runCatching {
      quranFileManager.copyFromAssetsRelative(DATABASE_NAME, temporaryName, directory)
      File(directory, temporaryName).renameTo(file)
    }.getOrDefault(false)

    if (copied) {
      // a new app version ships a new database name, which leaves the old copy behind
      directory.listFiles { candidate ->
        candidate.name.startsWith(DATABASE_PREFIX) && !candidate.name.startsWith(DATABASE_NAME)
      }?.forEach { it.delete() }
    }
    return copied
  }

  private companion object {
    const val DATABASE_PREFIX = "hadith_v"
    const val DATABASE_NAME = "${DATABASE_PREFIX}1.db"
  }
}
