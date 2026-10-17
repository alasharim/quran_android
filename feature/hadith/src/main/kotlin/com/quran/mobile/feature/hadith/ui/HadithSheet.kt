package com.quran.mobile.feature.hadith.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.hadith.R
import com.quran.mobile.feature.hadith.data.HadithCollection
import com.quran.mobile.feature.hadith.data.LinkedHadith
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

@Composable
internal fun HadithSheet(
  ayahName: String,
  suraName: String,
  hadiths: ImmutableList<LinkedHadith>?,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
      .background(MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(top = 18.dp, start = 18.dp, end = 18.dp)) {
      Box(
        modifier = Modifier
          .align(Alignment.CenterHorizontally)
          .padding(bottom = 14.dp)
          .size(width = 36.dp, height = 4.dp)
          .clip(RoundedCornerShape(percent = 100))
          .background(MaterialTheme.colorScheme.outlineVariant)
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp)
      ) {
        Text(
          text = stringResource(R.string.hadith_title),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
        )
        Text(
          text = ayahName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 9.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = stringResource(R.string.hadith_close),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .clip(RoundedCornerShape(percent = 100))
            .clickable(onClick = onClose)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }
      HorizontalDivider()
    }

    when {
      hadiths == null -> Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(32.dp)
      ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
      }

      hadiths.isEmpty() -> Text(
        text = stringResource(R.string.hadith_none),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
      )

      else -> HadithList(hadiths = hadiths, suraName = suraName)
    }
  }
}

@Composable
private fun HadithList(hadiths: ImmutableList<LinkedHadith>, suraName: String) {
  val aboutAyah = hadiths.filterNot { it.isAboutWholeSura }
  val aboutSura = hadiths.filter { it.isAboutWholeSura }

  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxSize()
  ) {
    if (aboutAyah.isNotEmpty()) {
      item(key = "ayah-header") {
        SectionHeader(stringResource(R.string.hadith_section_ayah))
      }
      items(aboutAyah, key = { "ayah-${it.id}" }) { HadithCard(it) }
    }

    if (aboutSura.isNotEmpty()) {
      item(key = "sura-header") {
        SectionHeader(stringResource(R.string.hadith_section_sura, suraName))
      }
      items(aboutSura, key = { "sura-${it.id}" }) { HadithCard(it) }
    }

    item(key = "source") {
      Text(
        text = stringResource(R.string.hadith_source_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
      )
    }
  }
}

@Composable
private fun SectionHeader(text: String) {
  Text(
    text = text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)
  )
}

@Composable
private fun HadithCard(hadith: LinkedHadith) {
  var isExpanded by rememberSaveable(hadith.id) { mutableStateOf(false) }
  val isLong = hadith.text.length > LONG_HADITH_LENGTH
  val uriHandler = LocalUriHandler.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val collectionName = stringResource(hadith.collection.nameResource())
  val reference = stringResource(R.string.hadith_reference, collectionName, hadith.number)

  Surface(
    shape = RoundedCornerShape(14.dp),
    // the theme leaves surfaceContainerLow at the Material default, which clashes with its palette
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = reference,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f)
        )
        GradeChip(hadith.grade)
      }

      // the chapter heading and the hadith are Arabic whatever the app's language
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column {
          hadith.chapter?.let { chapter ->
            Text(
              text = chapter,
              style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Start,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
          }

          Text(
            text = hadith.text,
            style = MaterialTheme.typography.bodyLarge.copy(
              fontSize = 19.sp,
              lineHeight = 34.sp,
              textDirection = TextDirection.Rtl
            ),
            textAlign = TextAlign.Start,
            maxLines = if (isLong && !isExpanded) COLLAPSED_LINES else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLong) {
          TextButton(onClick = { isExpanded = !isExpanded }) {
            Text(
              stringResource(if (isExpanded) R.string.hadith_show_less else R.string.hadith_show_more)
            )
          }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
          onClick = {
            scope.launch {
              val label = reference
              clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText(label, "${hadith.text}\n\n$reference"))
              )
            }
          }
        ) {
          Text(stringResource(R.string.hadith_copy))
        }
        TextButton(onClick = { uriHandler.openUri(hadith.sunnahUrl) }) {
          Text(stringResource(R.string.hadith_read_english))
        }
      }
    }
  }
}

@Composable
private fun GradeChip(grade: String) {
  val label = if (grade == SAHIH) stringResource(R.string.hadith_grade_sahih) else grade
  Text(
    text = label,
    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onTertiaryContainer,
    modifier = Modifier
      .clip(RoundedCornerShape(percent = 100))
      .background(MaterialTheme.colorScheme.tertiaryContainer)
      .padding(horizontal = 10.dp, vertical = 3.dp)
  )
}

private fun HadithCollection.nameResource(): Int = when (this) {
  HadithCollection.BUKHARI -> R.string.hadith_collection_bukhari
  HadithCollection.MUSLIM -> R.string.hadith_collection_muslim
}

private const val SAHIH = "صحيح"
private const val LONG_HADITH_LENGTH = 600
private const val COLLAPSED_LINES = 8

@Preview
@Composable
private fun HadithSheetPreview() {
  QuranTheme {
    HadithSheet(
      ayahName = "Al-Fātiḥah 1:7",
      suraName = "Al-Fātiḥah",
      hadiths = persistentListOf(
        LinkedHadith(
          id = 2,
          collection = HadithCollection.BUKHARI,
          number = "4475",
          chapter = "باب {غير المغضوب عليهم ولا الضالين} [الفاتحة: 7]",
          text = "حَدَّثَنَا عَبْدُ اللَّهِ بْنُ يُوسُفَ، أَخْبَرَنَا مَالِكٌ، عَنْ سُمَىٍّ، عَنْ أَبِي صَالِحٍ، " +
            "عَنْ أَبِي هُرَيْرَةَ أَنَّ رَسُولَ اللَّهِ صلى الله عليه وسلم قَالَ \" إِذَا قَالَ الإِمَامُ " +
            "{غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلاَ الضَّالِّينَ} فَقُولُوا آمِينَ \"",
          grade = SAHIH,
          isAboutWholeSura = false
        ),
        LinkedHadith(
          id = 1,
          collection = HadithCollection.BUKHARI,
          number = "4474",
          chapter = "باب ما جاء في فاتحة الكتاب",
          text = "حَدَّثَنَا مُسَدَّدٌ، حَدَّثَنَا يَحْيَى، عَنْ شُعْبَةَ ...",
          grade = SAHIH,
          isAboutWholeSura = true
        )
      ),
      onClose = {}
    )
  }
}
