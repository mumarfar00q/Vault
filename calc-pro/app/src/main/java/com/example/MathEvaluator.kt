package com.example

import kotlin.math.*

class MathEvaluator(private val isDegree: Boolean = false) {
    
    fun evaluate(expression: String): Double {
        val balanced = balanceParentheses(expression)
        val sanitized = sanitize(balanced)
        if (sanitized.trim().isEmpty()) return 0.0
        val valResult = Parser(sanitized, isDegree).parse()
        // If the result is a NaN or Infinite, throw an evaluation error
        if (valResult.isNaN() || valResult.isInfinite()) {
            throw ArithmeticException("Invalid mathematical result")
        }
        return valResult
    }

    private fun balanceParentheses(expr: String): String {
        val openCount = expr.count { it == '(' }
        val closeCount = expr.count { it == ')' }
        val missing = openCount - closeCount
        return if (missing > 0) {
            expr + ")".repeat(missing)
        } else {
            expr
        }
    }

    private fun sanitize(expr: String): String {
        val withImplicitMult = insertImplicitMultiplication(expr)
        return withImplicitMult
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("√", "sqrt")
    }

    private fun insertImplicitMultiplication(expr: String): String {
        val sb = StringBuilder()
        for (i in expr.indices) {
            val current = expr[i]
            sb.append(current)
            if (i < expr.length - 1) {
                val next = expr[i + 1]
                
                // Tracing what tokens need implicit multiplication:
                // If current character is a digit, parenthesis, superscript, or %, and next character is standard letter, open brace or root symbol.
                val isCurrentValueLike = current.isDigit() || current == ')' || current == '²' || current == '³' || current == '%'
                val isNextActionLike = next == '(' || next.isLetter() || next == '√'
                
                if (isCurrentValueLike && isNextActionLike) {
                    sb.append("×") // Inserts implicit multiply
                }
            }
        }
        return sb.toString()
    }

    private class Parser(private val input: String, private val isDegree: Boolean) {
        private var pos = -1
        private var ch = ' '

        private fun nextChar() {
            pos++
            ch = if (pos < input.length) input[pos] else '\u0000'
        }

        private fun eat(charToEat: Char): Boolean {
            while (ch == ' ') nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            // Check for trailing unexpected chars
            while (ch == ' ') nextChar()
            if (pos < input.length) {
                throw RuntimeException("Unexpected character: $ch")
            }
            return x
        }

        // Expression = Term (+ Term, - Term)
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+') -> x += parseTerm()
                    eat('-') -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        // Term = Factor (* Factor, / Factor)
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*') -> x *= parseFactor()
                    eat('/') -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        // Factor = Unary (+, -) Factor | Primary base
        private fun parseFactor(): Double {
            if (eat('+')) return parseFactor()
            if (eat('-')) return -parseFactor()

            var x: Double
            val startPos = this.pos
            if (eat('(')) {
                x = parseExpression()
                if (!eat(')')) throw RuntimeException("Unbalanced parenthesis")
            } else if (ch in '0'..'9' || ch == '.') {
                while (ch in '0'..'9' || ch == '.') {
                    nextChar()
                }
                val numStr = input.substring(startPos, this.pos)
                x = numStr.toDoubleOrNull() ?: throw RuntimeException("Invalid number")
            } else if (ch in 'A'..'Z' || ch in 'a'..'z') {
                while (ch in 'A'..'Z' || ch in 'a'..'z') {
                    nextChar()
                }
                val funcName = input.substring(startPos, this.pos)
                if (eat('(')) {
                    val arg = parseExpression()
                    if (!eat(')')) throw RuntimeException("Unbalanced parenthesis for function")
                    x = evalFunction(funcName, arg)
                } else {
                    throw RuntimeException("Functions must be followed by parentheses")
                }
            } else {
                throw RuntimeException("Unexpected syntax: '$ch'")
            }

            // Postfix powers and percent
            while (true) {
                if (eat('²')) {
                    x = x.pow(2.0)
                } else if (eat('³')) {
                    x = x.pow(3.0)
                } else if (eat('%')) {
                    x /= 100.0
                } else {
                    break
                }
            }

            return x
        }

        private fun evalFunction(name: String, arg: Double): Double {
            return when (name.lowercase()) {
                "sin" -> {
                    val rad = if (isDegree) Math.toRadians(arg) else arg
                    val r = sin(rad)
                    if (abs(r) < 1e-15) 0.0 else if (abs(r - 1.0) < 1e-15) 1.0 else if (abs(r + 1.0) < 1e-15) -1.0 else r
                }
                "cos" -> {
                    val rad = if (isDegree) Math.toRadians(arg) else arg
                    val r = cos(rad)
                    if (abs(r) < 1e-15) 0.0 else if (abs(r - 1.0) < 1e-15) 1.0 else if (abs(r + 1.0) < 1e-15) -1.0 else r
                }
                "tan" -> {
                    val rad = if (isDegree) Math.toRadians(arg) else arg
                    val c = cos(rad)
                    if (abs(c) < 1e-15) throw ArithmeticException("Tangent undefined")
                    val r = tan(rad)
                    if (abs(r) < 1e-15) 0.0 else r
                }
                "log" -> {
                    if (arg <= 0.0) throw ArithmeticException("Invalid log domain")
                    log10(arg)
                }
                "ln" -> {
                    if (arg <= 0.0) throw ArithmeticException("Invalid ln domain")
                    ln(arg)
                }
                "sqrt" -> {
                    if (arg < 0.0) throw ArithmeticException("Invalid sqrt domain")
                    sqrt(arg)
                }
                else -> throw RuntimeException("Unknown function: $name")
            }
        }
    }
}
