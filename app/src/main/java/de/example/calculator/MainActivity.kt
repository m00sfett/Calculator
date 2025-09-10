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

// --- Kotlin-Grundlagen anschaulich demonstriert ---
// Eine data class in Kotlin enthält nur Daten.
// Hier speichern wir den kompletten Zustand der Benutzeroberfläche.
data class CalculatorUiState(
    val input: String = "",        // aktuelle Eingabe, als Text gespeichert
    val expression: String = "",   // kompletter Ausdruck, der oben angezeigt wird
    val ans: Double? = null,       // letztes Ergebnis; kann mit ANS erneut genutzt werden
    val error: String? = null      // Fehlermeldung, null bedeutet kein Fehler
)

class CalculatorViewModel : ViewModel() {
    var uiState by mutableStateOf(CalculatorUiState())
        private set

    // Bereich: Funktionen, die auf Tastendrücke reagieren

    // Wird aufgerufen, wenn der Nutzer eine Ziffer drückt.
    // Fügt die Ziffer an das aktuelle Eingabefeld an.
    fun onDigit(ch: Char) {
        if (ch in '0'..'9') {
            if (uiState.expression.contains("=")) {
                uiState = uiState.copy(expression = "", input = "")
            }
            updateInput(uiState.input + ch)
        }
    }

    // Fügt einen Dezimalpunkt hinzu, falls noch keiner vorhanden ist.
    fun onDot() {
        if (uiState.expression.contains("=")) {
            uiState = uiState.copy(expression = "", input = "")
        }
        if (!uiState.input.contains('.')) {
            val newInput = if (uiState.input.isEmpty()) "0." else uiState.input + "."
            updateInput(newInput)
        }
    }

    // Verarbeitet Operatoren wie +, -, * oder /.
    // Die aktuelle Eingabe wird an den Ausdruck angehängt
    // und anschließend der Operator eingefügt.
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

    // Öffnende Klammer setzen.
    fun onOpenParen() {
        var expr = uiState.expression
        if (expr.contains("=")) expr = ""
        expr += "("
        uiState = uiState.copy(expression = expr, error = null)
    }

    // Schließende Klammer setzen.
    // Falls vorher noch eine Zahl im Eingabefeld steht,
    // wird sie zuerst in den Ausdruck übernommen.
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

    // Berechnet den Ausdruck und zeigt das Ergebnis an.
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

    // Setzt das letzte Ergebnis erneut als Eingabe.
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

    // Ende des Bereichs für Eingabe-Handler

    // Bereich: Hilfsfunktionen, unterstützen Berechnung und Anzeige

    private fun setError(msg: String) {
        uiState = uiState.copy(error = msg)
    }

    // Formatiert eine Zahl so, dass maximal zehn Nachkommastellen angezeigt werden.
    private fun format(x: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("#.##########", symbols)
        return df.format(x)
    }

    // Aktualisiert die Eingabe und beschränkt die Länge auf 24 Zeichen.
    private fun updateInput(newValue: String) {
        val trimmed = newValue.take(24)
        uiState = uiState.copy(input = trimmed, error = null)
    }

    // Zerlegt den Ausdruck, wandelt ihn um und berechnet das Ergebnis.
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

    // Zerlegt einen Rechenausdruck in einzelne Bestandteile (Token).
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

    // Wandelt eine Infix-Notation (z.B. 2+3) in eine Postfix-Notation um.
    // Diese Methode basiert auf dem Shunting-Yard-Algorithmus.
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

    // Rechnet eine Liste in Postfix-Notation aus.
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

    // Ende des Hilfsfunktions-Bereichs
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

// Hauptoberfläche des Taschenrechners.
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
        // Zeile 1: Löschen, Klammern und ANS
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("C", Modifier.weight(1f)) { vm.onClear() }
            CalcButton("(", Modifier.weight(1f)) { vm.onOpenParen() }
            CalcButton(")", Modifier.weight(1f)) { vm.onCloseParen() }
            CalcButton("ANS", Modifier.weight(1f)) { vm.onAns() }
        }
        // Zeile 2: Zahlen 7 bis 9 und Division
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("7", Modifier.weight(1f)) { vm.onDigit('7') }
            CalcButton("8", Modifier.weight(1f)) { vm.onDigit('8') }
            CalcButton("9", Modifier.weight(1f)) { vm.onDigit('9') }
            CalcButton("÷", Modifier.weight(1f)) { vm.onOperator('/') }
        }
        // Zeile 3: Zahlen 4 bis 6 und Multiplikation
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("4", Modifier.weight(1f)) { vm.onDigit('4') }
            CalcButton("5", Modifier.weight(1f)) { vm.onDigit('5') }
            CalcButton("6", Modifier.weight(1f)) { vm.onDigit('6') }
            CalcButton("×", Modifier.weight(1f)) { vm.onOperator('*') }
        }
        // Zeile 4: Zahlen 1 bis 3 und Subtraktion
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("1", Modifier.weight(1f)) { vm.onDigit('1') }
            CalcButton("2", Modifier.weight(1f)) { vm.onDigit('2') }
            CalcButton("3", Modifier.weight(1f)) { vm.onDigit('3') }
            CalcButton("−", Modifier.weight(1f)) { vm.onOperator('-') }
        }
        // Zeile 5: Null, Dezimalpunkt, Ergebnis und Addition
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

// Zeigt Ausdruck, aktuelle Eingabe oder das letzte Ergebnis sowie Fehler an.
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

// Einfacher Button mit einheitlicher Höhe.
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

// Hilfsfunktion zum Formatieren von Zahlen für die Anzeige.
private fun formatStatic(x: Double): String =
    DecimalFormat("#.##########", DecimalFormatSymbols(Locale.US)).format(x)
