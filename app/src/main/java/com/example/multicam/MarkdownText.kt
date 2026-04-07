package com.example.multicam

import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val currentColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(currentColor)

                val markwon = Markwon.builder(context)
                    .usePlugin(MarkwonInlineParserPlugin.create())
                    .usePlugin(JLatexMathPlugin.create(this.textSize))
                    .build()

                tag = markwon
            }
        },
        update = { view ->
            view.setTextColor(currentColor)

            val markwon = view.tag as Markwon
            markwon.setMarkdown(view, markdown)
        }
    )
}