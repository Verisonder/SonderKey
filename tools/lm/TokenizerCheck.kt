// SPDX-License-Identifier: GPL-3.0-only
// Checks the app's tokenizer against the reference: every sample must encode to exactly the same
// ids. Run by the language model workflow; exits non-zero on any difference.
import helium314.keyboard.latin.lm.LmTokenizer
import helium314.keyboard.latin.lm.LmWords
import java.io.File
import java.util.Base64
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val tokenizer = File(args[0]).bufferedReader().useLines { LmTokenizer.parse(it.iterator()) }
    var failed = 0
    for (line in File(args[1]).readLines()) {
        if (line.isBlank()) continue
        val (b64, idText) = line.split('\t')
        val text = String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
        val expected = if (idText.isEmpty()) emptyList() else idText.split(',').map { it.toInt() }
        val got = tokenizer.encode(text).toList()
        if (got != expected) {
            failed++
            println("::error title=tokenizer::mismatch for ${text.take(40)}: expected ${expected.take(12)} got ${got.take(12)}")
        }
    }
    val words = LmWords(tokenizer)
    println("whole-word tokens: ${words.size}")
    if (words.size < 5000) {
        println("::error title=tokenizer::only ${words.size} whole-word tokens")
        failed++
    }
    println(if (failed == 0) "tokenizer check passed" else "tokenizer check failed: $failed")
    exitProcess(if (failed == 0) 0 else 1)
}
