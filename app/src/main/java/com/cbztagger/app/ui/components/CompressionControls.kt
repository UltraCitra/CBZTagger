package com.cbztagger.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbztagger.app.R
import kotlin.math.roundToInt

/**
 * 1) Equal-sized segmented toggle between "기존 유지" (-1) and "압축" (0..9)
 * 2) When "압축" is selected, shows an integer 0..9 Slider with a paragraph description underneath
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipCompressionControl(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialSliderLevel: Int = 6,
) {
    val isCompressMode = level >= 0
    var lastSliderLevel by remember(initialSliderLevel) {
        mutableIntStateOf(if (level in 0..9) level else initialSliderLevel.coerceIn(0, 9))
    }

    LaunchedEffect(level) {
        if (level in 0..9) {
            lastSliderLevel = level
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.zip_section_label),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isCompressMode) {
                    stringResource(R.string.zip_level_value, level.coerceIn(0, 9))
                } else {
                    stringResource(R.string.zip_mode_keep)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedButton(
                selected = !isCompressMode,
                onClick = { onLevelChange(-1) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.zip_mode_keep),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SegmentedButton(
                selected = isCompressMode,
                onClick = { onLevelChange(lastSliderLevel) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    text = stringResource(R.string.zip_mode_compress),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AnimatedContent(
            targetState = isCompressMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top))
                    .togetherWith(
                        fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Top),
                    )
                    .using(SizeTransform(clip = true))
            },
            label = "zip_compression_mode",
        ) { compress ->
            if (compress) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(6.dp))
                    ZipCompressionSlider(
                        level = if (level in 0..9) level else lastSliderLevel,
                        onLevelChange = { newLevel ->
                            lastSliderLevel = newLevel
                            onLevelChange(newLevel)
                        },
                        showHeader = false,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.zip_keep_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Integer 0..9 slider for ZIP compression level (0 = Store, 1..9 = Deflate)
 * with a concise explanation paragraph below.
 */
@Composable
fun ZipCompressionSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val clamped = level.coerceIn(0, 9)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            Text(
                text = stringResource(R.string.zip_level_value, clamped),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = clamped.toFloat(),
            onValueChange = { raw ->
                val snapped = raw.roundToInt().coerceIn(0, 9)
                if (snapped != clamped) {
                    onLevelChange(snapped)
                }
            },
            valueRange = 0f..9f,
            steps = 8, // Interior ticks 1..8 -> exact integer snaps 0..9
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "9",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.zip_slider_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
