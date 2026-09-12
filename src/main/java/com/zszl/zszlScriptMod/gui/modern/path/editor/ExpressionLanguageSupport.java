package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java lexical highlighting and completion support for the
 * {@code LegacyActionRuntime} expression language.
 */
public final class ExpressionLanguageSupport {
    public static final int COLOR_WHITESPACE = 0xFFF3F7FA;
    public static final int COLOR_NUMBER = 0xFF79C7FF;
    public static final int COLOR_STRING = 0xFFF0B55E;
    public static final int COLOR_KEYWORD = 0xFFC792EA;
    public static final int COLOR_IDENTIFIER = 0xFFF3F7FA;
    public static final int COLOR_FUNCTION = 0xFF82AAFF;
    public static final int COLOR_OPERATOR = 0xFFF06B92;
    public static final int COLOR_DELIMITER = 0xFF9BAAB6;
    public static final int COLOR_COMMENT = 0xFF71808D;
    public static final int COLOR_UNKNOWN = 0xFFFF6B6B;

    private static final String[] MULTI_CHARACTER_OPERATORS = new String[] {
            "==", "!=", ">=", "<=", "&&", "||", "??", "?:",
            "+=", "-=", "*=", "/=", "%="
    };
    private static final String SINGLE_CHARACTER_OPERATORS = "+-*/%!><=";
    private static final String DELIMITERS = "()[]{}.,?:";

    /** Token categories returned by {@link #tokenize(String)}. */
    public enum TokenKind {
        WHITESPACE,
        NUMBER,
        QUOTED_STRING,
        KEYWORD,
        IDENTIFIER,
        FUNCTION_IDENTIFIER,
        OPERATOR,
        DELIMITER,
        COMMENT,
        UNKNOWN;

        /** Returns the renderer-independent ARGB color for this token kind. */
        public int color() {
            return colorFor(this);
        }
    }

    /** Completion categories suitable for icons or grouping in an editor. */
    public enum CompletionKind {
        VARIABLE,
        FUNCTION,
        KEYWORD,
        LITERAL,
        OPERATOR,
        DYNAMIC
    }

    /** Immutable token span. Offsets are zero-based and {@code end} is exclusive. */
    public static final class Span {
        public final int start;
        public final int end;
        public final String text;
        public final TokenKind kind;

        public Span(int start, int end, String text, TokenKind kind) {
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("Invalid span offsets: " + start + ".." + end);
            }
            if (text == null) {
                throw new IllegalArgumentException("Span text cannot be null");
            }
            if (text.length() != end - start) {
                throw new IllegalArgumentException("Span text length does not match its offsets");
            }
            this.start = start;
            this.end = end;
            this.text = text;
            this.kind = kind == null ? TokenKind.UNKNOWN : kind;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public String getText() {
            return text;
        }

        public TokenKind getKind() {
            return kind;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Span)) {
                return false;
            }
            Span span = (Span) other;
            return start == span.start && end == span.end && text.equals(span.text) && kind == span.kind;
        }

        @Override
        public int hashCode() {
            int result = start;
            result = 31 * result + end;
            result = 31 * result + text.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return kind + "(" + start + "," + end + ",\"" + text + "\")";
        }
    }

    /** Immutable completion metadata consumed by expression editors. */
    public static final class Completion {
        public final String insertText;
        public final String label;
        public final String detail;
        public final String description;
        public final CompletionKind kind;

        public Completion(String insertText, String label, String detail, String description, CompletionKind kind) {
            this.insertText = safe(insertText);
            this.label = safe(label);
            this.detail = safe(detail);
            this.description = safe(description);
            this.kind = kind == null ? CompletionKind.DYNAMIC : kind;
        }

        public String getInsertText() {
            return insertText;
        }

        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public String getDescription() {
            return description;
        }

        public CompletionKind getKind() {
            return kind;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Completion)) {
                return false;
            }
            Completion completion = (Completion) other;
            return insertText.equals(completion.insertText)
                    && label.equals(completion.label)
                    && detail.equals(completion.detail)
                    && description.equals(completion.description)
                    && kind == completion.kind;
        }

        @Override
        public int hashCode() {
            int result = insertText.hashCode();
            result = 31 * result + label.hashCode();
            result = 31 * result + detail.hashCode();
            result = 31 * result + description.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return label.isEmpty() ? insertText : label;
        }
    }

    private static final Comparator<Completion> COMPLETION_COMPARATOR = new Comparator<Completion>() {
        @Override
        public int compare(Completion left, Completion right) {
            int compared = compareText(left.label, right.label);
            if (compared != 0) {
                return compared;
            }
            compared = compareText(left.insertText, right.insertText);
            if (compared != 0) {
                return compared;
            }
            compared = left.kind.name().compareTo(right.kind.name());
            if (compared != 0) {
                return compared;
            }
            compared = compareText(left.detail, right.detail);
            if (compared != 0) {
                return compared;
            }
            return compareText(left.description, right.description);
        }
    };

    public static final List<Completion> BUILTIN_VARIABLE_COMPLETIONS;
    public static final List<Completion> FUNCTION_COMPLETIONS;
    public static final List<Completion> KEYWORD_COMPLETIONS;
    public static final List<Completion> OPERATOR_COMPLETIONS;
    public static final List<Completion> DEFAULT_COMPLETIONS;

    static {
        List<Completion> variables = new ArrayList<Completion>();
        addVariables(variables, "Runtime variable", "Legacy runtime context value.",
                "sequence_name", "step_index", "action_index", "gui_title", "current_screen");
        addVariables(variables, "Player variable", "Legacy flat player runtime value.",
                "player_x", "player_y", "player_z",
                "player_health", "player_food", "player_name", "player_yaw", "player_pitch",
                "player_block_x", "player_block_y", "player_block_z");
        addVariables(variables, "Player namespace", "Namespaced alias for a player runtime value.",
                "player.x", "player.y", "player.z",
                "player.health", "player.food", "player.name", "player.yaw", "player.pitch",
                "player.block_x", "player.block_y", "player.block_z", "player.uuid",
                "player.max_health", "player.saturation", "player.armor", "player.air",
                "player.experience", "player.experience_level", "player.selected_slot",
                "player.dimension", "player.on_ground", "player.sneaking", "player.sprinting",
                "player.burning", "gui.title", "gui.screen");
        addVariables(variables, "Dropped-item filter field", "Fields available to dropped-item and experience-orb filters.",
                "name", "id", "registry", "type", "entitytype", "count", "stacksize", "slot", "damage",
                "meta", "hasnbt", "nbtraw", "rawnbt", "tooltip", "lore", "rarity", "distance", "dist",
                "xp", "xpvalue", "experience", "experiencevalue", "alltext", "search");
        BUILTIN_VARIABLE_COMPLETIONS = immutableSorted(variables);

        List<Completion> functions = new ArrayList<Completion>();
        addFunctions(functions, "Captured ID function", "Reads a captured ID as hexadecimal text or decimal.",
                "cap", "captured", "capturedid", "capdec", "captureddec", "capturediddec");
        addFunctions(functions, "Text predicate", "Tests text or collection content and text boundaries.",
                "contains", "containsignorecase", "startswith", "startswithignorecase",
                "endswith", "endswithignorecase", "equalsignorecase");
        addFunctions(functions, "Regular expression", "Matches text or extracts a regular-expression group.",
                "regex", "matches", "regexgroup", "regexcapture", "capturegroup", "regexextract");
        addFunctions(functions, "Text function", "Transforms, searches, splits, or joins text.",
                "trim", "lower", "lowercase", "upper", "uppercase", "replace",
                "substring", "substr", "indexof", "lastindexof", "split", "join");
        addFunctions(functions, "Value function", "Selects a fallback value or tests value presence.",
                "coalesce", "if", "empty", "exists");
        addFunctions(functions, "Collection function", "Inspects or combines collection values.",
                "len", "size", "count", "first", "last", "any", "all");
        addFunctions(functions, "Comparison function", "Compares values using runtime expression semantics.",
                "eq", "ne", "gt", "lt", "gte", "lte", "between", "betweeninc", "betweeninclusive");
        addFunctions(functions, "Math function", "Calculates numeric aggregate, range, or rounding results.",
                "min", "max", "sum", "avg", "average", "abs", "pow", "clamp",
                "round", "floor", "ceil");
        addFunctions(functions, "Random function", "Generates a random integer or floating-point value.",
                "random", "rand", "randomint", "randomfloat");
        addFunctions(functions, "Conversion function", "Converts expression values between supported types.",
                "tonumber", "number", "int", "toint", "hextodec", "hexdec",
                "toboolean", "bool", "boolean", "tostring", "string");
        FUNCTION_COMPLETIONS = immutableSorted(functions);

        List<Completion> keywords = new ArrayList<Completion>();
        keywords.add(new Completion("true", "true", "Boolean literal", "Boolean true value.", CompletionKind.KEYWORD));
        keywords.add(new Completion("false", "false", "Boolean literal", "Boolean false value.", CompletionKind.KEYWORD));
        keywords.add(new Completion("null", "null", "Null literal", "Null value.", CompletionKind.KEYWORD));
        keywords.add(new Completion("nil", "nil", "Null literal", "Alias for null.", CompletionKind.KEYWORD));
        KEYWORD_COMPLETIONS = immutableSorted(keywords);

        List<Completion> operators = new ArrayList<Completion>();
        addOperators(operators, "Comparison operator", "Compares the left and right values.",
                "==", "!=", ">=", "<=", ">", "<");
        addOperators(operators, "Logical operator", "Combines or negates boolean values.",
                "&&", "||", "!");
        addOperators(operators, "Null/conditional operator", "Selects a fallback or conditional value.",
                "??", "?:");
        addOperators(operators, "Arithmetic operator", "Calculates a numeric or concatenated value.",
                "+", "-", "*", "/", "%");
        addOperators(operators, "Assignment operator", "Assignment or set-var shorthand operator.",
                "=", "+=", "-=", "*=", "/=", "%=");
        OPERATOR_COMPLETIONS = immutableSorted(operators);

        List<Completion> defaults = new ArrayList<Completion>();
        defaults.addAll(BUILTIN_VARIABLE_COMPLETIONS);
        defaults.addAll(FUNCTION_COMPLETIONS);
        defaults.addAll(KEYWORD_COMPLETIONS);
        defaults.addAll(OPERATOR_COMPLETIONS);
        DEFAULT_COMPLETIONS = Collections.unmodifiableList(defaults);
    }

    private ExpressionLanguageSupport() {
    }

    /** Returns the renderer-independent ARGB color for a token kind. */
    public static int colorFor(TokenKind kind) {
        if (kind == null) {
            return COLOR_UNKNOWN;
        }
        switch (kind) {
            case WHITESPACE:
                return COLOR_WHITESPACE;
            case NUMBER:
                return COLOR_NUMBER;
            case QUOTED_STRING:
                return COLOR_STRING;
            case KEYWORD:
                return COLOR_KEYWORD;
            case IDENTIFIER:
                return COLOR_IDENTIFIER;
            case FUNCTION_IDENTIFIER:
                return COLOR_FUNCTION;
            case OPERATOR:
                return COLOR_OPERATOR;
            case DELIMITER:
                return COLOR_DELIMITER;
            case COMMENT:
                return COLOR_COMMENT;
            case UNKNOWN:
            default:
                return COLOR_UNKNOWN;
        }
    }

    /** Tokenizes an expression into an immutable, contiguous list of spans. */
    public static List<Span> tokenize(String expression) {
        String source = expression == null ? "" : expression;
        if (source.isEmpty()) {
            return Collections.emptyList();
        }

        List<Span> spans = new ArrayList<Span>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            int end;

            if (Character.isWhitespace(current)) {
                end = index + 1;
                while (end < source.length() && Character.isWhitespace(source.charAt(end))) {
                    end++;
                }
                addSpan(spans, source, index, end, TokenKind.WHITESPACE);
                index = end;
                continue;
            }

            if (current == '#' || (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/')) {
                end = index + (current == '#' ? 1 : 2);
                while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '\r') {
                    end++;
                }
                addSpan(spans, source, index, end, TokenKind.COMMENT);
                index = end;
                continue;
            }

            if (current == '\'' || current == '"') {
                end = scanQuotedString(source, index, current);
                addSpan(spans, source, index, end, TokenKind.QUOTED_STRING);
                index = end;
                continue;
            }

            if (isNumberStart(source, index)) {
                end = scanNumber(source, index);
                addSpan(spans, source, index, end, TokenKind.NUMBER);
                index = end;
                continue;
            }

            if (isIdentifierStart(current)) {
                end = index + 1;
                while (end < source.length() && isIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String identifier = source.substring(index, end);
                TokenKind kind;
                if (isKeyword(identifier)) {
                    kind = TokenKind.KEYWORD;
                } else {
                    int lookahead = end;
                    while (lookahead < source.length() && Character.isWhitespace(source.charAt(lookahead))) {
                        lookahead++;
                    }
                    kind = identifier.charAt(0) != '$'
                            && lookahead < source.length() && source.charAt(lookahead) == '('
                                    ? TokenKind.FUNCTION_IDENTIFIER : TokenKind.IDENTIFIER;
                }
                addSpan(spans, source, index, end, kind);
                index = end;
                continue;
            }

            String operator = operatorAt(source, index);
            if (operator != null) {
                end = index + operator.length();
                addSpan(spans, source, index, end, TokenKind.OPERATOR);
                index = end;
                continue;
            }

            if (isDelimiter(current)) {
                addSpan(spans, source, index, index + 1, TokenKind.DELIMITER);
                index++;
                continue;
            }

            end = index + 1;
            while (end < source.length() && !startsKnownToken(source, end)) {
                end++;
            }
            addSpan(spans, source, index, end, TokenKind.UNKNOWN);
            index = end;
        }
        return Collections.unmodifiableList(spans);
    }

    /**
     * Returns the identifier/path prefix immediately before the cursor. Dollar
     * prefixes, dotted paths, and numeric bracket indexes are retained.
     */
    public static String currentTokenPrefix(String expression, int cursorOffset) {
        String source = expression == null ? "" : expression;
        int cursor = clamp(cursorOffset, 0, source.length());
        if (cursor == 0 || isInsideStringOrComment(source, cursor)) {
            return "";
        }
        return currentTokenPrefixUnchecked(source, cursor);
    }

    private static String currentTokenPrefixUnchecked(String source, int cursor) {
        int start = cursor;
        while (start > 0) {
            char current = source.charAt(start - 1);
            if (isIdentifierPart(current)) {
                start--;
                continue;
            }
            if (current == '.') {
                if (start > 1 && (isIdentifierPart(source.charAt(start - 2))
                        || source.charAt(start - 2) == ']')) {
                    start--;
                    continue;
                }
                break;
            }
            if (current == ']') {
                start--;
                continue;
            }
            if (current == '[') {
                boolean attachedToPath = start > 1 && (isIdentifierPart(source.charAt(start - 2))
                        || source.charAt(start - 2) == ']');
                if (attachedToPath) {
                    start--;
                    continue;
                }
                break;
            }
            if ((current == '-' || current == '+') && start > 1 && source.charAt(start - 2) == '[') {
                start--;
                continue;
            }
            break;
        }
        return source.substring(start, cursor);
    }

    /**
     * Returns immutable, deterministic completion results for the prefix at the
     * cursor. Dynamic candidates win case-insensitive insertion-text duplicates.
     */
    public static List<Completion> complete(String expression, int cursorOffset,
            Collection<Completion> dynamicCandidates) {
        String source = expression == null ? "" : expression;
        int cursor = clamp(cursorOffset, 0, source.length());
        if (isInsideStringOrComment(source, cursor)) {
            return Collections.emptyList();
        }
        String prefix = currentTokenPrefixUnchecked(source, cursor);
        List<Completion> result = new ArrayList<Completion>();
        Set<String> seen = new LinkedHashSet<String>();

        appendCandidates(result, seen, dynamicCandidates, prefix, true);
        appendCandidates(result, seen, BUILTIN_VARIABLE_COMPLETIONS, prefix, false);
        appendCandidates(result, seen, FUNCTION_COMPLETIONS, prefix, false);
        appendCandidates(result, seen, KEYWORD_COMPLETIONS, prefix, false);
        appendCandidates(result, seen, OPERATOR_COMPLETIONS, prefix, false);
        return Collections.unmodifiableList(result);
    }

    private static void appendCandidates(List<Completion> result, Set<String> seen,
            Collection<Completion> source, String prefix, boolean dynamic) {
        if (source == null || source.isEmpty()) {
            return;
        }

        List<Completion> matches = new ArrayList<Completion>();
        for (Completion candidate : source) {
            Completion prepared = prepareCandidate(candidate, prefix, dynamic);
            if (prepared != null) {
                matches.add(prepared);
            }
        }
        Collections.sort(matches, COMPLETION_COMPARATOR);
        for (Completion candidate : matches) {
            String key = completionKey(candidate);
            if (!key.isEmpty() && seen.add(key)) {
                result.add(candidate);
            }
        }
    }

    private static Completion prepareCandidate(Completion candidate, String prefix, boolean dynamic) {
        if (candidate == null || (candidate.insertText.isEmpty() && candidate.label.isEmpty())) {
            return null;
        }

        String actualPrefix = prefix == null ? "" : prefix;
        boolean dollarPrefix = actualPrefix.startsWith("$");
        String matchPrefix = dollarPrefix ? actualPrefix.substring(1) : actualPrefix;
        boolean variableCandidate = candidate.kind == CompletionKind.VARIABLE
                || (dynamic && candidate.kind == CompletionKind.DYNAMIC);

        String insertForMatch = candidate.insertText;
        String labelForMatch = candidate.label;
        if (dollarPrefix) {
            if (insertForMatch.startsWith("$")) {
                insertForMatch = insertForMatch.substring(1);
            } else if (!variableCandidate) {
                return null;
            }
            if (labelForMatch.startsWith("$")) {
                labelForMatch = labelForMatch.substring(1);
            }
        }

        if (!startsWithIgnoreCase(insertForMatch, matchPrefix)
                && !startsWithIgnoreCase(labelForMatch, matchPrefix)) {
            return null;
        }

        if (dollarPrefix && variableCandidate && !candidate.insertText.startsWith("$")) {
            return new Completion("$" + candidate.insertText, candidate.label, candidate.detail,
                    candidate.description, candidate.kind);
        }
        return candidate;
    }

    private static String completionKey(Completion completion) {
        String key = completion.insertText.isEmpty() ? completion.label : completion.insertText;
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        String safeValue = safe(value);
        String safePrefix = safe(prefix);
        return safeValue.length() >= safePrefix.length()
                && safeValue.regionMatches(true, 0, safePrefix, 0, safePrefix.length());
    }

    private static int scanQuotedString(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '\\' && index < source.length()) {
                index++;
            } else if (current == quote) {
                break;
            }
        }
        return index;
    }

    private static boolean isNumberStart(String source, int index) {
        char current = source.charAt(index);
        return isAsciiDigit(current)
                || (current == '.' && index + 1 < source.length() && isAsciiDigit(source.charAt(index + 1)));
    }

    private static int scanNumber(String source, int start) {
        int index = start;
        if (source.charAt(index) == '.') {
            index++;
        }
        while (index < source.length() && isAsciiDigit(source.charAt(index))) {
            index++;
        }
        if (index < source.length() && source.charAt(index) == '.') {
            index++;
            while (index < source.length() && isAsciiDigit(source.charAt(index))) {
                index++;
            }
        }

        if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
            int exponent = index + 1;
            if (exponent < source.length() && (source.charAt(exponent) == '+' || source.charAt(exponent) == '-')) {
                exponent++;
            }
            int digits = exponent;
            while (exponent < source.length() && isAsciiDigit(source.charAt(exponent))) {
                exponent++;
            }
            if (exponent > digits) {
                index = exponent;
            }
        }
        return index;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private static boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    private static boolean isKeyword(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                || "null".equalsIgnoreCase(value) || "nil".equalsIgnoreCase(value);
    }

    private static String operatorAt(String source, int index) {
        for (String operator : MULTI_CHARACTER_OPERATORS) {
            if (source.startsWith(operator, index)) {
                return operator;
            }
        }
        char current = source.charAt(index);
        return SINGLE_CHARACTER_OPERATORS.indexOf(current) >= 0 ? String.valueOf(current) : null;
    }

    private static boolean isDelimiter(char value) {
        return DELIMITERS.indexOf(value) >= 0;
    }

    private static boolean startsKnownToken(String source, int index) {
        char current = source.charAt(index);
        return Character.isWhitespace(current)
                || current == '#'
                || (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/')
                || current == '\'' || current == '"'
                || isNumberStart(source, index)
                || isIdentifierStart(current)
                || operatorAt(source, index) != null
                || isDelimiter(current);
    }

    private static void addSpan(List<Span> spans, String source, int start, int end, TokenKind kind) {
        spans.add(new Span(start, end, source.substring(start, end), kind));
    }

    private static boolean isInsideStringOrComment(String source, int cursor) {
        char quote = 0;
        boolean escaped = false;
        boolean lineComment = false;

        for (int index = 0; index < cursor; index++) {
            char current = source.charAt(index);
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '#') {
                lineComment = true;
                continue;
            }
            if (current == '/' && index + 1 < cursor && source.charAt(index + 1) == '/') {
                lineComment = true;
                index++;
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            }
        }
        return quote != 0 || lineComment;
    }

    private static void addVariables(List<Completion> target, String detail, String description, String... names) {
        for (String name : names) {
            target.add(new Completion(name, name, detail, description, CompletionKind.VARIABLE));
        }
    }

    private static void addFunctions(List<Completion> target, String detail, String description, String... names) {
        for (String name : names) {
            target.add(new Completion(name, name + "(...)", detail, description, CompletionKind.FUNCTION));
        }
    }

    private static void addOperators(List<Completion> target, String detail, String description, String... names) {
        for (String name : names) {
            target.add(new Completion(name, name, detail, description, CompletionKind.OPERATOR));
        }
    }

    private static List<Completion> immutableSorted(Collection<Completion> source) {
        List<Completion> result = new ArrayList<Completion>(source);
        Collections.sort(result, COMPLETION_COMPARATOR);
        return Collections.unmodifiableList(result);
    }

    private static int compareText(String left, String right) {
        int compared = safe(left).compareToIgnoreCase(safe(right));
        return compared != 0 ? compared : safe(left).compareTo(safe(right));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
