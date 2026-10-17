package com.quran.mobile.feature.hadith

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.hadith.data.HadithRepository
import com.quran.mobile.feature.hadith.data.LinkedHadith
import com.quran.mobile.feature.hadith.di.HadithSheetWrapperInjector
import com.quran.mobile.feature.hadith.ui.HadithSheet
import com.quran.page.common.data.QuranNaming
import dev.zacsweers.metro.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class HadithSheetWrapper(
  context: Context,
  private val currentAyah: SuraAyah,
  private val onDismissed: () -> Unit
) : FrameLayout(context) {

  @Inject
  lateinit var hadithRepository: HadithRepository

  @Inject
  lateinit var quranNaming: QuranNaming

  init {
    (context as? HadithSheetWrapperInjector)?.injectHadithSheetWrapper(this)

    val composeView = ComposeView(context).apply {
      setContent {
        QuranTheme {
          HadithBottomSheet()
        }
      }
    }
    addView(composeView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  @Composable
  private fun HadithBottomSheet() {
    val hadiths by produceState<ImmutableList<LinkedHadith>?>(null, currentAyah) {
      value = hadithRepository.hadithsForAyah(currentAyah).toImmutableList()
    }

    val context = LocalContext.current
    val ayahName = remember(context, currentAyah) {
      quranNaming.getSuraAyahString(context, currentAyah.sura, currentAyah.ayah)
    }
    val suraName = remember(context, currentAyah) {
      quranNaming.getSuraName(context, currentAyah.sura)
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
      bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false
      )
    )
    val hasOpened = remember { mutableStateOf(false) }
    val peekHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
      // delay so the sheet animates smoothly, otherwise it just snaps open
      delay(50.milliseconds)
      // peeked by default - the user can drag it up to read more at once
      scaffoldState.bottomSheetState.partialExpand()
      hasOpened.value = true
    }

    // fires once the sheet is hidden, whether by the close button or a swipe
    LaunchedEffect(hasOpened.value, scaffoldState.bottomSheetState.currentValue) {
      if (hasOpened.value && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
        onDismissed()
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .displayCutoutPadding()
    ) {
      BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        sheetDragHandle = null,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = Color.Transparent,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.surface),
        sheetContent = {
          HadithSheet(
            ayahName = ayahName,
            suraName = suraName,
            hadiths = hadiths,
            onClose = { scope.launch { scaffoldState.bottomSheetState.hide() } },
            modifier = Modifier.fillMaxHeight()
          )
        }
      ) {
        // no content behind the sheet - this wrapper only exists to host it
      }
    }
  }
}
