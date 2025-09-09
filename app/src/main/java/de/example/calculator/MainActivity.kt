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
import kotlin.math.sqrt
import java.text.DecimalFormat

// --- Kotlin basics in action ---
// data class: holds pure UI state data
data class CalculatorUiState(
    val input: String = "",        // current input buffer (as text)
    val ans: Double? = null,       // last result ("ANS")
    val pendingOp: Operation? = null,
    val firstOperand: Double? = null,
    val error: String? = null      // nullable = no error
)

// sealed class: closed set of operations => when() can be exhaustive
sealed class Operation {
    object Add : Operation()
    object Sub : Operation()
    object Mul : Operation()
    object Div : Operation()
}

class CalculatorViewModel : ViewModel() {

    var uiState by mutableStateOf(CalculatorUiState())
        private set

    // region Intent handlers ---------------------------------------------------

    fun onDigit(ch: Char) {
        // Guard: allow only digits 0-9
        if (ch in '0'..'9') {
            updateInput(uiState.input + ch)
        }
    }

    fun onDot() {
        // Prevent multiple dots
        if (!uiState.input.contains('.')) {
            val newInput = if (uiState.input.isEmpty()) "0." else uiState.input + "."
            updateInput(newInput)
        }
    }

    fun onClear() {
        uiState = CalculatorUiState(ans = uiState.ans) // keep ANS, clear rest
    }

    fun onUnarySquare() {
        val x = currentNumberOrAns() ?: return
        val result = x * x
        commitResult(result)
    }

    fun onUnarySqrt() {
        val x = currentNumberOrAns() ?: return
        if (x < 0.0) {
            setError("Fehler: Wurzel aus negativer Zahl")
            return
        }
        val result = sqrt(x)
        commitResult(result)
    }

    fun onBinary(op: Operation) {
        val x = parseInput()
        // If no current input, allow chaining with ANS
        val operand = x ?: uiState.ans
        if (operand == null) {
            // Nothing to operate on; just stash the operation and wait
            uiState = uiState.copy(pendingOp = op)
            return
        }
        uiState = uiState.copy(
            firstOperand = operand,
            pendingOp = op,
            input = "",
            error = null
        )
    }

    fun onEquals() {
        val a = uiState.firstOperand
        val b = parseInput()
        val op = uiState.pendingOp
        if (a == null || b == null || op == null) return

        val result = when (op) {
            is Operation.Add -> a + b
            is Operation.Sub -> a - b
            is Operation.Mul -> a * b
            is Operation.Div -> {
                if (b == 0.0) {
                    setError("Fehler: Division durch 0")
                    return
                } else a / b
            }
        }
        commitResult(result)
    }

    fun onAns() {
        val a = uiState.ans ?: return
        updateInput(format(a))
    }

    // endregion ---------------------------------------------------------------

    // region Helpers -----------------------------------------------------------

    private fun currentNumberOrAns(): Double? = parseInput() ?: uiState.ans

    private fun parseInput(): Double? =
        uiState.input.toDoubleOrNull()

    private fun commitResult(result: Double) {
        uiState = uiState.copy(
            input = format(result),
            ans = result,
            firstOperand = null,
            pendingOp = null,
            error = null
        )
    }

    private fun setError(msg: String) {
        uiState = uiState.copy(error = msg)
    }

    // Format double like a calculator (trim trailing zeros)
    private fun format(x: Double): String {
        val df = DecimalFormat("#.##########") // up to 10 decimals
        return df.format(x)
    }

    private fun updateInput(newValue: String) {
        // Optional: constrain length to avoid huge numbers
        val trimmed = newValue.take(24)
        uiState = uiState.copy(input = trimmed, error = null)
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DisplayArea(
            value = s.input.ifEmpty { s.ans?.let { "(ANS) ${formatStatic(it)}" } ?: "0" },
            error = s.error
        )
        Spacer(Modifier.height(4.dp))
        // Row 1
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("C", Modifier.weight(1f)) { vm.onClear() }
            CalcButton("ANS", Modifier.weight(1f)) { vm.onAns() }
            CalcButton("x²", Modifier.weight(1f)) { vm.onUnarySquare() }
            CalcButton("√x", Modifier.weight(1f)) { vm.onUnarySqrt() }
        }
        // Row 2
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("7", Modifier.weight(1f)) { vm.onDigit('7') }
            CalcButton("8", Modifier.weight(1f)) { vm.onDigit('8') }
            CalcButton("9", Modifier.weight(1f)) { vm.onDigit('9') }
            CalcButton("÷", Modifier.weight(1f)) { vm.onBinary(Operation.Div) }
        }
        // Row 3
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("4", Modifier.weight(1f)) { vm.onDigit('4') }
            CalcButton("5", Modifier.weight(1f)) { vm.onDigit('5') }
            CalcButton("6", Modifier.weight(1f)) { vm.onDigit('6') }
            CalcButton("×", Modifier.weight(1f)) { vm.onBinary(Operation.Mul) }
        }
        // Row 4
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("1", Modifier.weight(1f)) { vm.onDigit('1') }
            CalcButton("2", Modifier.weight(1f)) { vm.onDigit('2') }
            CalcButton("3", Modifier.weight(1f)) { vm.onDigit('3') }
            CalcButton("−", Modifier.weight(1f)) { vm.onBinary(Operation.Sub) }
        }
        // Row 5
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalcButton("0", Modifier.weight(1f)) { vm.onDigit('0') }
            CalcButton(",", Modifier.weight(1f)) { vm.onDot() } // German decimal label, still uses '.'
            CalcButton("=", Modifier.weight(1f)) { vm.onEquals() }
            CalcButton("+", Modifier.weight(1f)) { vm.onBinary(Operation.Add) }
        }
        Text(
            text = "Hinweis: ANS setzt das letzte Ergebnis als Eingabe.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun DisplayArea(value: String, error: String?) {
    Column(Modifier.fillMaxWidth()) {
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
private fun formatStatic(x: Double): String = DecimalFormat("#.##########").format(x)
