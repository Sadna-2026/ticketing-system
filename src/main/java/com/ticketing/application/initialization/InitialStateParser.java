package com.ticketing.application.initialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses an optional initial-state file (V3-14) into an ordered list of
 * {@link InitialStateOperation}s. The file is a sequence of use-case calls; this class
 * only turns the text into a structured, ordered list — it does <strong>not</strong>
 * execute anything. Execution against the application layer is issue #273.
 *
 * <h2>Format</h2>
 * <pre>
 *   file        := (operation | comment | blank-line)*
 *   operation   := name '(' [ arg (',' arg)* ] ')' ';'
 *   arg         := quoted-string | bare-token
 * </pre>
 * <ul>
 *   <li>Each operation is {@code operation-name(arg1, arg2, ...);} terminated by a
 *       semicolon. An operation may span multiple lines.</li>
 *   <li>Whitespace and newlines between tokens are insignificant.</li>
 *   <li>Comment lines start with {@code #} or {@code //} (after optional leading
 *       whitespace) and are skipped, along with blank lines.</li>
 *   <li>A double-quoted argument may contain commas, spaces, semicolons and {@code //};
 *       the surrounding quotes are stripped. {@code \"} and {@code \\} are recognised as
 *       escapes inside a quoted argument. Unquoted (bare) arguments are trimmed.</li>
 *   <li>Zero-arg calls are written {@code op()} and yield an empty argument list.</li>
 * </ul>
 * Malformed input (unterminated call, missing parenthesis, unbalanced quote) throws
 * {@link InitialStateParseException} with the offending line number and context.
 * Empty, whitespace-only or comment-only content yields an empty list.
 *
 * <p>No Spring annotations — this is a plain, stateless parser wired by #273.
 */
public class InitialStateParser {

    private static final Logger log = LoggerFactory.getLogger(InitialStateParser.class);

    /**
     * Parses initial-state file content into an ordered list of operations.
     *
     * @param content the full file text; {@code null} is treated as empty
     * @param sourceFile the source file name for logging
     * @return the operations in the order they appear; empty if there are none
     * @throws InitialStateParseException if the content is malformed
     */
    public List<InitialStateOperation> parse(String content, String sourceFile) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<InitialStateOperation> operations = new ArrayList<>();
        int i = 0;
        int line = 1;
        int n = content.length();

        while (i < n) {
            char c = content.charAt(i);

            // Track line numbers and skip insignificant whitespace between operations.
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // Comment line: '#...' or '//...' until end of line.
            if (c == '#' || (c == '/' && i + 1 < n && content.charAt(i + 1) == '/')) {
                while (i < n && content.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }

            // A bare '/' that is not the start of a '//' comment is invalid here.
            if (c == '/') {
                throw error(line, "unexpected '/' (use '//' for comments)", content, i, sourceFile);
            }

            // Otherwise we are at the start of an operation.
            ParseResult result = parseOperation(content, i, line, sourceFile);
            operations.add(result.operation());
            line = result.endLine();
            i = result.endIndex();
        }

        return operations;
    }

    /**
     * Convenience: reads the file at the given path (UTF-8) and parses it.
     *
     * @param path the file to read
     * @return the parsed operations
     * @throws InitialStateParseException if the content is malformed
     * @throws UncheckedIOException       if the file cannot be read
     */
    public List<InitialStateOperation> parse(Path path) {
        try {
            return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read initial-state file: " + path, e);
        }
    }

    /**
     * Convenience: reads the stream fully (UTF-8) and parses it. The caller owns the
     * stream and is responsible for closing it.
     *
     * @param in the stream to read
     * @return the parsed operations
     * @throws InitialStateParseException if the content is malformed
     * @throws UncheckedIOException       if the stream cannot be read
     */
    public List<InitialStateOperation> parse(InputStream in, String sourceFile) {
        try {
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), sourceFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read initial-state stream", e);
        }
    }

    /**
     * Parses a single {@code name(args);} operation starting at {@code start}.
     */
    private ParseResult parseOperation(String content, int start, int startLine, String sourceFile) {
        int n = content.length();
        int line = startLine;
        int i = start;

        // --- operation name (up to the opening parenthesis) ---
        int nameStart = i;
        while (i < n && content.charAt(i) != '(' && content.charAt(i) != ';') {
            if (content.charAt(i) == '\n') {
                line++;
            }
            i++;
        }
        if (i >= n || content.charAt(i) != '(') {
            throw error(startLine, "missing '(' after operation name", content, start, sourceFile);
        }
        String name = content.substring(nameStart, i).trim();
        if (name.isEmpty()) {
            throw error(startLine, "missing operation name", content, start, sourceFile);
        }
        if (name.indexOf('\n') >= 0 || hasInteriorWhitespace(name)) {
            throw error(startLine, "operation name must be a single token: '" + name + "'", content, start, sourceFile);
        }
        i++; // consume '('

        // --- arguments ---
        // Each argument slot is, after trimming surrounding whitespace, either a
        // double-quoted string (quotes stripped, escapes resolved) or a bare token
        // (trimmed). Whitespace outside a token is insignificant.
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean sawAnyToken = false;    // distinguishes op() from op( , )
        boolean quotedThisArg = false;  // current slot was given as a quoted string
        boolean closedQuoteThisArg = false; // a quoted token has already ended in this slot

        while (true) {
            if (i >= n) {
                throw error(line, "unterminated operation (missing ')')", content, start, sourceFile);
            }
            char c = content.charAt(i);

            if (c == '"') {
                if (quotedThisArg || hasNonWhitespace(current)) {
                    throw error(line, "quoted string may not be mixed with other text in an argument",
                            content, i, sourceFile);
                }
                quotedThisArg = true;
                sawAnyToken = true;
                current.setLength(0); // discard leading whitespace before the opening quote
                QuoteScan scan = readQuotedString(content, i + 1, line, start, current, sourceFile);
                i = scan.endIndex();
                line = scan.endLine();
                closedQuoteThisArg = true;
                continue;
            }
            if (c == ',') {
                args.add(finishArg(current, quotedThisArg));
                current.setLength(0);
                quotedThisArg = false;
                closedQuoteThisArg = false;
                sawAnyToken = true; // a comma implies a preceding argument slot
                i++;
                continue;
            }
            if (c == ')') {
                // Close the call. Add the trailing argument unless this is a zero-arg op().
                String trailing = finishArg(current, quotedThisArg);
                if (sawAnyToken || !trailing.isEmpty()) {
                    args.add(trailing);
                }
                i++;
                break;
            }
            if (c == '\n') {
                line++;
                if (!closedQuoteThisArg) {
                    current.append(c); // insignificant trailing whitespace after a quote is dropped
                }
                i++;
                continue;
            }
            if (closedQuoteThisArg && !Character.isWhitespace(c)) {
                throw error(line, "unexpected text after closing quote in argument", content, i, sourceFile);
            }
            if (closedQuoteThisArg) {
                // Whitespace after a closed quote is insignificant; do not retain it.
                i++;
                continue;
            }
            current.append(c);
            if (!Character.isWhitespace(c)) {
                sawAnyToken = true;
            }
            i++;
        }

        // --- terminating semicolon (only whitespace allowed before it) ---
        while (i < n && content.charAt(i) != ';') {
            char c = content.charAt(i);
            if (c == '\n') {
                line++;
            }
            if (!Character.isWhitespace(c)) {
                throw error(line, "expected ';' after ')' but found '" + c + "'", content, i, sourceFile);
            }
            i++;
        }
        if (i >= n) {
            throw error(line, "missing ';' terminating operation '" + name + "'", content, start, sourceFile);
        }
        i++; // consume ';'

        return new ParseResult(new InitialStateOperation(name, args, startLine, sourceFile), i, line);
    }

    /**
     * Reads a double-quoted string whose opening quote has already been consumed, starting
     * at {@code start}. Appends the unescaped content (without the surrounding quotes) to
     * {@code out}. {@code \"} and {@code \\} are recognised escapes; newlines inside the
     * string are preserved.
     *
     * @return the scan position just after the closing quote, plus the updated line number
     * @throws InitialStateParseException if the string is never closed (unbalanced quote)
     */
    private QuoteScan readQuotedString(String content, int start, int line, int opStart, StringBuilder out, String sourceFile) {
        int n = content.length();
        int i = start;
        while (i < n) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char next = content.charAt(i + 1);
                if (next == '"' || next == '\\') {
                    out.append(next);
                    i += 2;
                    continue;
                }
            }
            if (c == '"') {
                return new QuoteScan(i + 1, line);
            }
            if (c == '\n') {
                line++;
            }
            out.append(c);
            i++;
        }
        throw error(line, "unbalanced quote (string is never closed)", content, opStart, sourceFile);
    }

    /**
     * Finalizes one argument: a quoted arg keeps its (already-unescaped) content verbatim;
     * a bare arg is trimmed.
     */
    private static String finishArg(StringBuilder raw, boolean quoted) {
        return quoted ? raw.toString() : raw.toString().trim();
    }

    private static boolean hasInteriorWhitespace(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonWhitespace(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static InitialStateParseException error(int line, String message, String content, int index, String sourceFile) {
        log.error("[PARSE ERROR] {}:{}: {}", sourceFile != null ? sourceFile : "unknown", line, message);
        return new InitialStateParseException(
                "Initial-state parse error at line " + line + ": " + message
                        + " (near: \"" + snippet(content, index) + "\")");
    }

    /** A short single-line snippet of the source around {@code index}, for diagnostics. */
    private static String snippet(String content, int index) {
        int from = Math.max(0, index);
        int to = Math.min(content.length(), from + 30);
        return content.substring(from, to).replace("\n", "\\n").replace("\r", "");
    }

    /** Internal carrier for a parsed operation plus the scan position after it. */
    private record ParseResult(InitialStateOperation operation, int endIndex, int endLine) {
    }

    /** Internal carrier for the scan position and line number after a quoted string. */
    private record QuoteScan(int endIndex, int endLine) {
    }
}
