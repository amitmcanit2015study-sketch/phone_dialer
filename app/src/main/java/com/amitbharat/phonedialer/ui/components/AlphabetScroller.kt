package com.amitbharat.phonedialer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AlphabetScroller(
    letters: List<Char>,
    onLetterSelect: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return

    var scrollerHeight by remember { mutableIntStateOf(0) }
    var selectedLetter by remember { mutableStateOf<Char?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun handleScroll(y: Float) {
        if (scrollerHeight > 0) {
            val itemHeight = scrollerHeight.toFloat() / letters.size
            var index = (y / itemHeight).toInt()
            index = index.coerceIn(0, letters.size - 1)
            val letter = letters[index]
            if (selectedLetter != letter) {
                selectedLetter = letter
                onLetterSelect(letter)
            }
        }
    }

    Column(
        modifier = modifier
            .width(24.dp)
            .onGloballyPositioned { coordinates ->
                scrollerHeight = coordinates.size.height
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        handleScroll(offset.y)
                        tryAwaitRelease()
                        selectedLetter = null
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> handleScroll(offset.y) },
                    onDragEnd = { selectedLetter = null },
                    onDragCancel = { selectedLetter = null },
                    onDrag = { change, _ ->
                        handleScroll(change.position.y)
                    }
                )
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val isSelected = selectedLetter == letter
            Text(
                text = letter.toString(),
                fontSize = if (isSelected) 14.sp else 10.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
            )
        }
    }
}
