package herdr.dev.app.ui.util

import java.util.regex.Pattern

data class ParsedTerminalTurn(
    val tools: List<String> = emptyList(),
    val response: String = "",
    val rawTerminal: String = "",
)

object TerminalOutputParser {
    private val ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;]*[a-zA-Z]|\\[\\?[0-9]+[a-zA-Z]")

    fun cleanAnsi(text: String): String {
        val noAnsi = ANSI_PATTERN.matcher(text).replaceAll("")
        return noAnsi.replace("\r", "")
    }

    /**
     * Parses raw terminal output into tool executions (commands, file operations)
     * and the agent's actual textual response/conclusion.
     */
    fun parse(rawText: String): ParsedTerminalTurn {
        val clean = cleanAnsi(rawText).trim()
        if (clean.isBlank()) {
            return ParsedTerminalTurn(rawTerminal = "")
        }

        val lines = clean.lines()
        val toolActions = mutableListOf<String>()
        val responseLines = mutableListOf<String>()

        val toolPrefixes = listOf("● ", "• ", "* Running ", "* Viewing ", "* Editing ", "Running command: ")

        var inTool = false
        var toolBuffer = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            if (trimmed.matches(Regex("^(?:agy|under|_|claude|codex)>\\s*$"))) continue

            val isToolHeader = toolPrefixes.any { trimmed.startsWith(it) }
            if (isToolHeader) {
                if (toolBuffer.isNotEmpty()) {
                    toolActions.add(toolBuffer.toString().trim())
                    toolBuffer = StringBuilder()
                }
                inTool = true
                toolBuffer.append(trimmed)
            } else if (inTool) {
                if (trimmed.startsWith(" ") || trimmed.startsWith("\t") || trimmed.startsWith("> Task") || trimmed.startsWith("Output:")) {
                    toolBuffer.append(" ").append(trimmed)
                } else {
                    toolActions.add(toolBuffer.toString().trim())
                    toolBuffer = StringBuilder()
                    inTool = false
                    responseLines.add(line)
                }
            } else {
                responseLines.add(line)
            }
        }
        if (toolBuffer.isNotEmpty()) {
            toolActions.add(toolBuffer.toString().trim())
        }

        val finalResponse = responseLines.joinToString("\n").trim()

        return ParsedTerminalTurn(
            tools = toolActions,
            response = if (finalResponse.isNotBlank()) finalResponse else clean,
            rawTerminal = clean
        )
    }
}
