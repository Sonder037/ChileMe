package me.chile.app.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.commonmark.node.*
import org.commonmark.parser.Parser

private val markdownParser=Parser.builder().build()
// Reuse completed text layouts as a long reply grows; split only at real line breaks.
internal fun markdownSections(text: AnnotatedString): List<AnnotatedString> = buildList {
    var start=0
    do {
        val end=text.text.indexOf('\n',start+800).takeIf {it>=0}?:text.length
        add(text.subSequence(start,end))
        start=end+1
    } while(start<=text.length)
}

// Render locally: HTML is plain text, and image URLs never trigger network requests.
fun markdownText(source: String): AnnotatedString = buildAnnotatedString {
    var breakLength=0
    fun newline() {if(length>breakLength)append('\n');breakLength=length}
    fun render(node: Node,depth: Int) {
        if(depth>64)return
        fun children() {
            var child=node.firstChild
            var number=(node as? OrderedList)?.startNumber?:1
            while(child!=null) {
                if(node is BulletList || node is OrderedList) {
                    newline()
                    var ancestor=node.parent
                    var indent=0
                    while(ancestor!=null) {if(ancestor is ListItem)indent++;ancestor=ancestor.parent}
                    append("  ".repeat(indent.coerceAtMost(16)))
                    append(if(node is OrderedList)"${number++}. " else "• ")
                }
                render(child,depth+1);child=child.next
            }
        }
        when(node) {
            is org.commonmark.node.Text->append(node.literal)
            is StrongEmphasis->withStyle(SpanStyle(fontWeight=FontWeight.Bold)){children()}
            is Emphasis->withStyle(SpanStyle(fontStyle=FontStyle.Italic)){children()}
            is Code->withStyle(SpanStyle(fontFamily=FontFamily.Monospace)){append(node.literal)}
            is Heading->{newline();withStyle(SpanStyle(fontWeight=FontWeight.Bold,fontSize=if(node.level<=2)20.sp else 17.sp)){children()};append('\n')}
            is Paragraph->{children();newline();if(node.parent !is ListItem)append('\n');breakLength=length}
            is SoftLineBreak,is HardLineBreak->append('\n')
            is FencedCodeBlock->{newline();withStyle(SpanStyle(fontFamily=FontFamily.Monospace)){append(node.literal)};newline()}
            is IndentedCodeBlock->{newline();withStyle(SpanStyle(fontFamily=FontFamily.Monospace)){append(node.literal)};newline()}
            is BlockQuote->{newline();append("│ ");children()}
            is ThematicBreak->{newline();append("──────\n")}
            is HtmlInline->append(node.literal)
            is HtmlBlock->{append(node.literal);newline()}
            else->children()
        }
    }
    render(markdownParser.parse(source.take(50000)),0)
}.let {it.subSequence(0,it.text.trimEnd().length)}

@Composable fun MarkdownText(source: String) {
    // Initial content is immediate; later stream updates keep the last layout while parsing.
    var rendered by remember {mutableStateOf(source to markdownSections(markdownText(source)))}
    LaunchedEffect(source) {
        if(rendered.first!=source)rendered=source to withContext(Dispatchers.Default){markdownSections(markdownText(source))}
    }
    SelectionContainer {Column {rendered.second.forEach {Text(it)}}}
}
