package com.oneuiclone.home.ui

import java.math.BigDecimal
import java.math.MathContext

/**
 * The calculator in search: "10x10", "(2+3)*4", "50/4", "2^8", "20%*50" ... A plain number is not a calculation
 * (it has to use an operator), and anything that isn't arithmetic gives null so it searches as usual.
 */
object Calculator {

    /** The answer for [input] as text ("100", "12.5"), or null when [input] isn't a calculation. */
    fun evaluate(input: String): String? {
        val s = input.trim().replace('x', '*').replace('X', '*').replace('×', '*').replace('÷', '/')
            .replace(',', '.').replace(" ", "")
        if (s.isEmpty() || s.none { it.isDigit() }) return null
        if (s.any { !(it.isDigit() || it in ".+-*/%^()") }) return null
        val p = Parser(s)
        val v = try { p.parse() } catch (_: Exception) { return null }
        if (!p.usedOperator || v.isNaN() || v.isInfinite()) return null
        return format(v)
    }

    private fun format(v: Double): String {
        val d = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        val plain = d.toPlainString()
        return if (plain.length <= 18) plain else String.format("%.6e", v)
    }

    private class Parser(private val s: String) {
        private var i = 0
        var usedOperator = false

        fun parse(): Double {
            val v = expr()
            if (i != s.length) throw IllegalArgumentException()
            return v
        }

        private fun peek() = if (i < s.length) s[i] else '\u0000'

        private fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') {
                val op = s[i++]; usedOperator = true
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (peek() == '*' || peek() == '/') {
                val op = s[i++]; usedOperator = true
                val r = power()
                if (op == '/' && r == 0.0) throw ArithmeticException()
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (peek() == '^') { i++; usedOperator = true; return Math.pow(base, power()) }
            return base
        }

        private fun unary(): Double {
            if (peek() == '-') { i++; return -unary() }
            if (peek() == '+') { i++; return unary() }
            var v = primary()
            while (peek() == '%') { i++; v /= 100.0; usedOperator = true }
            return v
        }

        private fun primary(): Double {
            if (peek() == '(') {
                i++
                val v = expr()
                if (peek() != ')') throw IllegalArgumentException()
                i++
                return v
            }
            val start = i
            while (peek().isDigit() || peek() == '.') i++
            if (start == i) throw IllegalArgumentException()
            return s.substring(start, i).toDouble()
        }
    }
}
