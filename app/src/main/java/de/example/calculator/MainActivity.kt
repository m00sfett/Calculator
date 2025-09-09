package de.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// --- Kotlin basics in action ---
// data class: holds pure UI state data
data class CalculatorUiState(
    val input: String = "",        // current input buffer (as text)
    val expression: String = "",   // full expression shown on top
    val ans: Double? = null,       // last result ("ANS")
    val error: String? = null      // nullable = no error
)

class CalculatorViewModel : ViewModel() {
    var uiState by mutableStateOf(CalculatorUiState())
        private set

    // region Intent handlers ---------------------------------------------------

    fun onDigit(ch: Char) {
        if (ch in '0'..'9') {
            if (uiState.expression.contains("=")) {
                uiState = uiState.copy(expression = "", input = "")
            }
            updateInput(uiState.input + ch)
        }
    }

    fun onDot() {
        if (uiState.expression.contains("=")) {
            uiState = uiState.copy(expression = "", input = "")
        }
        if (!uiState.input.contains('.')) {
            val newInput = if (uiState.input.isEmpty()) "0." else uiState.input + "."
            updateInput(newInput)
        }
    }

    fun onOperator(op: Char) {
        var expr = uiState.expression
        var inp = uiState.input
        if (expr.contains("=")) {
            expr = inp
        } else {
            if (inp.isNotEmpty()) expr += inp
        }
        if (expr.isEmpty()) return
        expr += op
        uiState = uiState.copy(expression = expr, input = "", error = null)
    }

    fun onOpenParen() {
        var expr = uiState.expression
        if (expr.contains("=")) expr = ""
        expr += "("
        uiState = uiState.copy(expression = expr, error = null)
    }

    fun onCloseParen() {
        var expr = uiState.expression
        var inp = uiState.input
        if (expr.contains("=")) {
            expr = ""
            inp = ""
        }
        if (inp.isNotEmpty()) {
            expr += inp
            inp = ""
        }
        expr += ")"
        uiState = uiState.copy(expression = expr, input = inp, error = null)
    }

    fun onEquals() {
        var expr = uiState.expression
        val inp = uiState.input
        if (expr.contains("=")) return
        if (inp.isNotEmpty()) expr += inp
        if (expr.isEmpty()) return
        val result = evaluateExpression(expr) ?: return
        uiState = uiState.copy(
            expression = "$expr =",
            input = format(result),
            ans = result,
            error = null
        )
    }

    fun onAns() {
        val a = uiState.ans ?: return
        if (uiState.expression.contains("=")) {
            uiState = uiState.copy(expression = "", input = "")
        }
        updateInput(format(a))
    }

    fun onClear() {
        uiState = uiState.copy(input = "", expression = "", error = null)
    }

    // endregion ---------------------------------------------------------------

    // region Helpers -----------------------------------------------------------

    private fun setError(msg: String) {
        uiState = uiState.copy(error = msg)
    }

    private fun format(x: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#.##########", symbols)
        return df.format(x)
    }

    private fun updateInput(newValue: String) {
        val trimmed = newValue.take(24)
        uiState = uiState.copy(input = trimmed, error = null)
    }

    private fun evaluateExpression(expr: String): Double? {
        return try {
            val tokens = tokenize(expr)
            val postfix = infixToPostfix(tokens)
            evalPostfix(postfix)
        } catch (e: IllegalArgumentException) {
            setError("Fehler: ${e.message}")
            null
        }
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    i++
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                c in charArrayOf('+', '-', '*', '/', '(', ')') -> {
                    tokens.add(c.toString())
                    i++
                }
                c.isWhitespace() -> i++
                else -> throw IllegalArgumentException("Ungültiges Zeichen")
            }
        }
        return tokens
    }

    private fun infixToPostfix(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        val stack = ArrayDeque<String>()
        val prec = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)
        for (t in tokens) {
            when {
                t.toDoubleOrNull() != null -> out.add(t)
                t in prec.keys -> {
                    while (stack.isNotEmpty() && stack.last() != "(" && prec[stack.last()]!! >= prec[t]!!) {
                        out.add(stack.removeLast())
                    }
                    stack.add(t)
                }
                t == "(" -> stack.add(t)
                t == ")" -> {
                    while (stack.isNotEmpty() && stack.last() != "(") {
                        out.add(stack.removeLast())
                    }
                    if (stack.isEmpty() || stack.removeLast() != "(") {
                        throw IllegalArgumentException("Klammerfehler")
                    }
                }
            }
        }
        while (stack.isNotEmpty()) {
            val op = stack.removeLast()
            if (op == "(") throw IllegalArgumentException("Klammerfehler")
            out.add(op)
        }
        return out
    }

    private fun evalPostfix(post: List<String>): Double {
        val stack = ArrayDeque<Double>()
        for (t in post) {
            when {
                t.toDoubleOrNull() != null -> stack.add(t.toDouble())
                t in listOf("+", "-", "*", "/") -> {
                    val b = stack.removeLastOrNull() ?: throw IllegalArgumentException("Ungültiger Ausdruck")
                    val a = stack.removeLastOrNull() ?: throw IllegalArgumentException("Ungültiger Ausdruck")
                    val res = when (t) {
                        "+" -> a + b
                        "-" -> a - b
                        "*" -> a * b
                        "/" -> {
                            if (b == 0.0) throw IllegalArgumentException("Division durch 0")
                            a / b
                        }
                        else -> 0.0
                    }
                    stack.add(res)
                }
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Ungültiger Ausdruck")
        return stack.last()
    }

    // endregion ---------------------------------------------------------------
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: CalculatorViewModel = viewModel()
                    CalculatorScreen(vm)
                }
            }
        }
    }
}

@Composable
fun CalculatorScreen(vm: CalculatorViewModel) {
    val s = vm.uiState
    val decimalSeparator = DecimalFormatSymbols(Locale.US).decimalSeparator
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DisplayArea(
            expression = s.expression.replace("*", "×").replace("/", "÷"),
            value = s.input.ifEmpty { s.ans?.let { "(ANS) ${formatStatic(it)}" } ?: "0" },
            error = s.error
        )
        Spacer(Modifier.height(4.dp))
        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("C", Modifier.weight(1f)) { vm.onClear() }
            CalcButton("(", Modifier.weight(1f)) { vm.onOpenParen() }
            CalcButton(")", Modifier.weight(1f)) { vm.onCloseParen() }
            CalcButton("ANS", Modifier.weight(1f)) { vm.onAns() }
        }
        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("7", Modifier.weight(1f)) { vm.onDigit('7') }
            CalcButton("8", Modifier.weight(1f)) { vm.onDigit('8') }
            CalcButton("9", Modifier.weight(1f)) { vm.onDigit('9') }
            CalcButton("÷", Modifier.weight(1f)) { vm.onOperator('/') }
        }
        // Row 3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("4", Modifier.weight(1f)) { vm.onDigit('4') }
            CalcButton("5", Modifier.weight(1f)) { vm.onDigit('5') }
            CalcButton("6", Modifier.weight(1f)) { vm.onDigit('6') }
            CalcButton("×", Modifier.weight(1f)) { vm.onOperator('*') }
        }
        // Row 4
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("1", Modifier.weight(1f)) { vm.onDigit('1') }
            CalcButton("2", Modifier.weight(1f)) { vm.onDigit('2') }
            CalcButton("3", Modifier.weight(1f)) { vm.onDigit('3') }
            CalcButton("−", Modifier.weight(1f)) { vm.onOperator('-') }
        }
        // Row 5
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("0", Modifier.weight(1f)) { vm.onDigit('0') }
            CalcButton(decimalSeparator.toString(), Modifier.weight(1f)) { vm.onDot() }
            CalcButton("=", Modifier.weight(1f)) { vm.onEquals() }
            CalcButton("+", Modifier.weight(1f)) { vm.onOperator('+') }
        }
        Text(
            text = "Hinweis: ANS setzt das letzte Ergebnis als Eingabe.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun DisplayArea(expression: String, value: String, error: String?) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = expression,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.End
            )
        }
        Divider(Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CalcButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier.height(56.dp),
        onClick = onClick,
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

// Utility for read-only formatting inside @Composable
private fun formatStatic(x: Double): String =
    DecimalFormat("#.##########", DecimalFormatSymbols(Locale.US)).format(x)
