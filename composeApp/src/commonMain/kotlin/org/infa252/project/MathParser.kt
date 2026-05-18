package org.infa252.project

class MathParser {

    enum class TokenType { NUMBER, OP, LPAREN, RPAREN, FUNCTION, POSTFIX, VARIABLE }
    data class Token(val type: TokenType, val value: String)

    private fun priority(op: String): Int = when (op) {
        "!" -> 5
        "sqrt", "sin", "cos", "tan", "ln", "log", "asin", "acos", "atan", "abs", "exp",
        "sinh", "cosh", "tanh" -> 4
        "^" -> 3
        "*", "/", "%" -> 2
        "+", "-" -> 1
        else -> 0
    }

    private val functions = setOf(
        "sqrt", "sin", "cos", "tan", "ln", "log", "asin", "acos", "atan", "abs", "exp",
        "fact", "floor", "ceil", "round", "sinh", "cosh", "tanh", "root"
    )

    private val constants = mapOf(
        "pi" to "3.14159265358979323846",
        "e" to "2.71828182845904523536"
    )

    fun tokenize(s: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var expectNumber = true
        
        while (i < s.length) {
            val c = s[i]
            if (c.isWhitespace() || c == ',') {
                i++
                continue
            }
            
            if (c == '-' && expectNumber) {
                val sb = StringBuilder("-")
                i++
                if (i < s.length && s[i] == '(') {
                    tokens.add(Token(TokenType.NUMBER, "0"))
                    tokens.add(Token(TokenType.OP, "-"))
                    expectNumber = true
                    continue
                }
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                    sb.append(s[i++])
                }
                // Handle scientific notation like -1.2e-5
                if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                    sb.append(s[i++])
                    if (i < s.length && (s[i] == '+' || s[i] == '-')) sb.append(s[i++])
                    while (i < s.length && s[i].isDigit()) sb.append(s[i++])
                }
                tokens.add(Token(TokenType.NUMBER, sb.toString()))
                expectNumber = false
                continue
            }
            
            if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                    sb.append(s[i++])
                }
                // Handle scientific notation like 1.2e5
                if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                    // Check if it's a number followed by 'e' (like 2e) or scientific notation
                    // If next is digit or +/- then it's scientific
                    val next = if (i + 1 < s.length) s[i+1] else ' '
                    if (next.isDigit() || next == '+' || next == '-') {
                        sb.append(s[i++])
                        if (i < s.length && (s[i] == '+' || s[i] == '-')) sb.append(s[i++])
                        while (i < s.length && s[i].isDigit()) sb.append(s[i++])
                    }
                }
                tokens.add(Token(TokenType.NUMBER, sb.toString()))
                expectNumber = false
            } else if (c.isLetter()) {
                val sb = StringBuilder()
                while (i < s.length && s[i].isLetter()) {
                    sb.append(s[i++])
                }
                val name = sb.toString()
                if (functions.contains(name)) {
                    tokens.add(Token(TokenType.FUNCTION, name))
                    expectNumber = true
                } else {
                    // Treat single letters as variables, multi-letter names as variables too if not functions
                    tokens.add(Token(TokenType.VARIABLE, name))
                    expectNumber = false
                }
            } else if (c == '|') {
                tokens.add(Token(TokenType.FUNCTION, "abs"))
                tokens.add(Token(TokenType.LPAREN, "("))
                i++

                val sb = StringBuilder()
                while (i < s.length && s[i] != '|') {
                    sb.append(s[i])
                    i++
                }

                tokens.addAll(tokenize(sb.toString()))

                tokens.add(Token(TokenType.RPAREN, ")"))

                if (i < s.length && s[i] == '|') {
                    i++
                }

                expectNumber = false
            } else if ("+-*/%^()!".contains(c)) {
                val type = when (c) {
                    '(' -> TokenType.LPAREN
                    ')' -> TokenType.RPAREN
                    '!' -> TokenType.POSTFIX
                    else -> TokenType.OP
                }
                tokens.add(Token(type, c.toString()))
                expectNumber = c == '(' || c == '+' || c == '-' || c == '*' || c == '/' || c == '^'
                i++
            } else {
                i++ // Ignore unknown
            }
        }
        return insertImplicitMultiplication(tokens)
    }

    private fun insertImplicitMultiplication(tokens: List<Token>): List<Token> {
        if (tokens.isEmpty()) return tokens
        val result = mutableListOf<Token>()
        result.add(tokens[0])
        for (i in 1 until tokens.size) {
            val prev = tokens[i - 1]
            val curr = tokens[i]

            val needsMult = when {
                prev.type == TokenType.NUMBER && curr.type == TokenType.VARIABLE -> true
                prev.type == TokenType.NUMBER && curr.type == TokenType.LPAREN -> true
                prev.type == TokenType.NUMBER && curr.type == TokenType.FUNCTION -> true
                prev.type == TokenType.VARIABLE && curr.type == TokenType.VARIABLE -> true
                prev.type == TokenType.VARIABLE && curr.type == TokenType.LPAREN -> true
                prev.type == TokenType.VARIABLE && curr.type == TokenType.FUNCTION -> true
                prev.type == TokenType.RPAREN && curr.type == TokenType.NUMBER -> true
                prev.type == TokenType.RPAREN && curr.type == TokenType.VARIABLE -> true
                prev.type == TokenType.RPAREN && curr.type == TokenType.LPAREN -> true
                prev.type == TokenType.POSTFIX && curr.type == TokenType.NUMBER -> true
                prev.type == TokenType.POSTFIX && curr.type == TokenType.VARIABLE -> true
                (prev.type == TokenType.OP && prev.value == "%") && (curr.type == TokenType.LPAREN || curr.type == TokenType.VARIABLE) -> true
                else -> false
            }

            if (needsMult) {
                result.add(Token(TokenType.OP, "*"))
            }
            result.add(curr)
        }
        return result
    }

    fun toRPN(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val ops = mutableListOf<Token>()
        
        for (t in tokens) {
            when (t.type) {
                TokenType.NUMBER, TokenType.VARIABLE, TokenType.POSTFIX -> output.add(t)
                TokenType.FUNCTION -> {
                    while (ops.isNotEmpty() && ops.last().value != "(" && priority(ops.last().value) >= priority(t.value)) {
                        output.add(ops.removeAt(ops.size - 1))
                    }
                    ops.add(t)
                }
                TokenType.LPAREN -> ops.add(t)
                TokenType.RPAREN -> {
                    if (ops.isNotEmpty() && ops.last().type == TokenType.LPAREN) {
                        throw IllegalArgumentException("Пустые скобки")
                    }
                    while (ops.isNotEmpty() && ops.last().value != "(") {
                        output.add(ops.removeAt(ops.size - 1))
                    }

                    if (ops.isEmpty()) {
                        throw IllegalArgumentException("Скобки расставлены неверно")
                    }

                    ops.removeAt(ops.size - 1)

                    if (ops.isNotEmpty() && ops.last().type == TokenType.FUNCTION) {
                        output.add(ops.removeAt(ops.size - 1))
                    }
                }
                TokenType.OP -> {
                    while (ops.isNotEmpty() && ops.last().value != "(" && priority(ops.last().value) >= priority(t.value)) {
                        output.add(ops.removeAt(ops.size - 1))
                    }
                    ops.add(t)
                }
            }
        }
        while (ops.isNotEmpty()) {
            if (ops.last().type == TokenType.LPAREN || ops.last().type == TokenType.RPAREN) {
                throw IllegalArgumentException("Скобки расставлены неверно")
            }
            output.add(ops.removeAt(ops.size - 1))
        }
        return output
    }

    fun evalRPN(rpn: List<Token>, variables: Map<String, BigNumber> = emptyMap()): BigNumber {
        val stack = mutableListOf<BigNumber>()
        for (t in rpn) {
            try {
                when (t.type) {
                    TokenType.NUMBER -> stack.add(BigNumber.fromString(t.value))
                    TokenType.VARIABLE -> {
                        val value = variables[t.value] 
                            ?: constants[t.value.lowercase()]?.let { BigNumber.fromString(it) }
                            ?: throw IllegalArgumentException("Переменная ${t.value} не определена")
                        stack.add(value)
                    }
                    TokenType.FUNCTION -> {
                        if (stack.isEmpty()) throw IllegalArgumentException("Недостаточно данных для ${t.value}")
                        
                        val res = when (t.value) {
                            "log" -> {
                                if (stack.size >= 2) {
                                    val valToLog = stack.removeAt(stack.size - 1)
                                    val base = stack.removeAt(stack.size - 1)
                                    valToLog.ln() / base.ln()
                                } else {
                                    stack.removeAt(stack.size - 1).log10()
                                }
                            }
                            "root" -> {
                                if (stack.size < 2) throw IllegalArgumentException("root требует 2 аргумента")
                                val n = stack.removeAt(stack.size - 1)
                                val x = stack.removeAt(stack.size - 1)
                                x.pow(BigNumber("1") / n)
                            }
                            "sqrt" -> stack.removeAt(stack.size - 1).sqrt()
                            "sin" -> stack.removeAt(stack.size - 1).sin()
                            "cos" -> stack.removeAt(stack.size - 1).cos()
                            "tan" -> stack.removeAt(stack.size - 1).tan()
                            "ln" -> stack.removeAt(stack.size - 1).ln()
                            "asin" -> stack.removeAt(stack.size - 1).asin()
                            "acos" -> stack.removeAt(stack.size - 1).acos()
                            "atan" -> stack.removeAt(stack.size - 1).atan()
                            "abs" -> stack.removeAt(stack.size - 1).abs()
                            "exp" -> stack.removeAt(stack.size - 1).exp()
                            "fact" -> stack.removeAt(stack.size - 1).factorial()
                            "floor" -> stack.removeAt(stack.size - 1).floor()
                            "ceil" -> stack.removeAt(stack.size - 1).ceil()
                            "round" -> stack.removeAt(stack.size - 1).round()
                            "sinh" -> stack.removeAt(stack.size - 1).sinh()
                            "cosh" -> stack.removeAt(stack.size - 1).cosh()
                            "tanh" -> stack.removeAt(stack.size - 1).tanh()
                            else -> throw IllegalArgumentException("Функция ${t.value} не поддерживается")
                        }
                        stack.add(res)
                    }
                    TokenType.POSTFIX -> {
                        if (stack.isEmpty()) throw IllegalArgumentException("Ошибка в операторе !")
                        val a = stack.removeAt(stack.size - 1)
                        stack.add(a.factorial())
                    }
                    TokenType.OP -> {
                        if (stack.size < 2) throw IllegalArgumentException("Ошибка в операции ${t.value}")
                        val b = stack.removeAt(stack.size - 1)
                        val a = stack.removeAt(stack.size - 1)
                        val res = when (t.value) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
                            "^" -> a.pow(b)
                            "%" -> a % b
                            else -> throw IllegalArgumentException("Оператор ${t.value} не поддерживается")
                        }
                        stack.add(res)
                    }
                    else -> {}
                }
            } catch (e: ArithmeticException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException(e.message ?: "Ошибка вычисления")
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Неверное выражение")
        return stack.last()
    }

    private fun getBalancedContent(s: String, startIndex: Int, open: Char, close: Char): Pair<String, Int>? {
        var count = 0
        var foundOpen = false
        var start = -1
        
        for (i in startIndex until s.length) {
            if (s[i] == open) {
                if (!foundOpen) {
                    foundOpen = true
                    start = i
                }
                count++
            } else if (s[i] == close) {
                count--
                if (count == 0 && foundOpen) {
                    return s.substring(start + 1, i) to i
                }
            }
        }
        return null
    }

    fun parseLatex(latex: String): String {
        var s = latex
            .replace("\\pi", "pi")
            .replace("\\cdot", "*")
            .replace("\\times", "*")
            .replace("\\div", "/")
            .replace("\\ ", "")
            .replace("\\sin", "sin")
            .replace("\\cos", "cos")
            .replace("\\tan", "tan")
            .replace("\\log_{10}", "log10")
            .replace("\\log_{2}", "log2")
            .replace("\\ln", "ln")
            .replace("\\log", "log")
            .replace("\\floor", "floor")
            .replace("\\ceil", "ceil")
            .replace("\\round", "round")
            .replace("\\arcsin", "asin")
            .replace("\\arccos", "acos")
            .replace("\\arctan", "atan")
            .replace("\\sin^{-1}", "asin")
            .replace("\\cos^{-1}", "acos")
            .replace("\\tan^{-1}", "atan")
            .replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\left[", "(")
            .replace("\\right]", ")")
            .replace("\\left|", "|")
            .replace("\\right|", "|")

        // 0. Handle \log_{base}{value} -> log(base, value)

        while (s.contains("\\log_{")) {
            val start = s.indexOf("\\log_{")
            val basePart = getBalancedContent(s, start + 5, '{', '}') ?: break
            val valPart = getBalancedContent(s, basePart.second + 1, '{', '}') ?: break
            s = s.substring(0, start) + "log(${basePart.first}, ${valPart.first})" + s.substring(valPart.second + 1)
        }
        // Handle \log_{10}{x} and \log_{2}{x} already covered by \log_{base}{value}
// Handle log10(x) -> log(10, x)
        while (s.contains("log10")) {
            val start = s.indexOf("log10")
            val content = getBalancedContent(s, start + 5, '(', ')')
                ?: getBalancedContent(s, start + 5, '{', '}')
                ?: break

            s = s.substring(0, start) + "log(10, ${content.first})" + s.substring(content.second + 1)
        }

// Handle log2(x) -> log(2, x)
        while (s.contains("log2")) {
            val start = s.indexOf("log2")
            val content = getBalancedContent(s, start + 4, '(', ')')
                ?: getBalancedContent(s, start + 4, '{', '}')
                ?: break

            s = s.substring(0, start) + "log(2, ${content.first})" + s.substring(content.second + 1)
        }

        // 1. Handle \sqrt[n]{x} -> root(x, n)
        while (s.contains("\\sqrt[")) {
            val start = s.indexOf("\\sqrt[")
            val nPart = getBalancedContent(s, start + 5, '[', ']') ?: break
            val xPart = getBalancedContent(s, nPart.second + 1, '{', '}') ?: break
            
            val n = nPart.first
            val x = xPart.first
            s = s.substring(0, start) + "root($x, $n)" + s.substring(xPart.second + 1)
        }

        // 2. Handle \frac{a}{b} -> (a)/(b)
        while (s.contains("\\frac")) {
            val start = s.indexOf("\\frac")
            val first = getBalancedContent(s, start + 5, '{', '}') ?: break
            val second = getBalancedContent(s, first.second + 1, '{', '}') ?: break
            
            s = s.substring(0, start) + "(${first.first})/(${second.first})" + s.substring(second.second + 1)
        }

        // 3. Handle \sqrt{x} -> sqrt(x)
        while (s.contains("\\sqrt")) {
            val start = s.indexOf("\\sqrt")
            val content = getBalancedContent(s, start + 5, '{', '}') ?: break
            s = s.substring(0, start) + "sqrt(${content.first})" + s.substring(content.second + 1)
        }

        // 4. Replace |x| with abs(x)
        val sb = StringBuilder()
        var openAbs = false

        for (char in s) {
            if (char == '|') {
                if (!openAbs) {
                    sb.append("abs(")
                    openAbs = true
                } else {
                    sb.append(")")
                    openAbs = false
                }
            } else {
                sb.append(char)
            }
        }

        if (openAbs) {
            throw IllegalArgumentException("Модуль не закрыт")
        }

        s = sb.toString()

        // 5. Replace remaining {} and []
        s = s.replace("{", "(").replace("}", ")").replace("[", "(").replace("]", ")")

        // 6. Final cleanup of LaTeX commands
        return s.replace("\\", "")
            .replace("asin", "asin") // ensure correct names for parser
            .replace("acos", "acos")
            .replace("atan", "atan")
    }

    fun evaluate(expression: String, variables: Map<String, String> = emptyMap()): String {
        return try {
            val latexHandled = parseLatex(expression)
            val tokens = tokenize(latexHandled)
            val rpn = toRPN(tokens)
            val varMap = variables.mapValues { BigNumber.fromString(it.value) }
            var res = evalRPN(rpn, varMap).toString()
            
            // Post-processing for rounding artifacts from fractional powers (Double conversion artifacts)
            if (res.contains(".")) {
                 val ninePattern = "999999"
                 val zeroPattern = "000000"
                 if (res.contains(ninePattern)) {
                     val idx = res.indexOf(ninePattern)
                     // If it's near the end and we have a fairly long string, it's likely a Double artifact
                     if (res.length - idx < 12 && res.length > 8) {
                         val prefix = res.substring(0, idx)
                         if (prefix.endsWith(".")) {
                             res = (prefix.substring(0, prefix.length - 1).toLong() + 1).toString()
                         } else {
                             res = prefix.substring(0, prefix.length - 1) + (prefix.last().digitToInt() + 1).toString()
                         }
                     }
                 } else if (res.contains(zeroPattern)) {
                     val idx = res.indexOf(zeroPattern)
                     if (res.length - idx < 12 && res.length > 8) {
                        res = res.substring(0, idx).trimEnd('.')
                     }
                 }
            }
            res
        } catch (e: ArithmeticException) {
            e.message ?: "Ошибка вычисления"
        } catch (e: IllegalArgumentException) {
            e.message ?: "Ошибка"
        } catch (e: Exception) {
            "Ошибка"
        }
    }
}
