package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class InitialStateParserTest {

    private final InitialStateParser parser = new InitialStateParser();

    @Test
    public void givenMultipleOperations_whenParse_thenReturnsThemInOrderWithNamesAndArgs() {
        String content = """
                login(rina, pw);
                open-production-company(rina_token, "Demo Co", "A demo company");
                """;

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(2, ops.size());
        assertEquals("login", ops.get(0).name());
        assertEquals(1, ops.get(0).line());
        assertEquals("test.txt", ops.get(0).sourceFile());
        assertEquals(List.of("rina", "pw"), ops.get(0).args());
        assertEquals("open-production-company", ops.get(1).name());
        assertEquals(List.of("rina_token", "Demo Co", "A demo company"), ops.get(1).args());
    }

    @Test
    public void givenQuotedArgWithCommaAndSpaces_whenParse_thenOneArgWithQuotesStripped() {
        String content = "create(\"Hello, world; and more\");";

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(1, ops.size());
        assertEquals(List.of("Hello, world; and more"), ops.get(0).args());
    }

    @Test
    public void givenUnquotedArgs_whenParse_thenTrimmed() {
        String content = "op(  a  ,   b  );";

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(List.of("a", "b"), ops.get(0).args());
    }

    @Test
    public void givenCommentsAndBlankLines_whenParse_thenSkipped() {
        String content = """
                # this is a hash comment
                // this is a slash comment

                login(rina, pw);   // trailing slash comment is its own line below
                // another comment
                logout(rina_token);
                """;

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(2, ops.size());
        assertEquals("login", ops.get(0).name());
        assertEquals("logout", ops.get(1).name());
    }

    @Test
    public void givenZeroArgCall_whenParse_thenEmptyArgList() {
        String content = "refresh();";

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(1, ops.size());
        assertEquals("refresh", ops.get(0).name());
        assertTrue(ops.get(0).args().isEmpty());
    }

    @Test
    public void givenOperationSpanningMultipleLines_whenParse_thenParsedAsOne() {
        String content = """
                open-production-company(
                    rina_token,
                    "Demo Co",
                    "A demo company"
                );
                """;

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(1, ops.size());
        assertEquals("open-production-company", ops.get(0).name());
        assertEquals(List.of("rina_token", "Demo Co", "A demo company"), ops.get(0).args());
    }

    @Test
    public void givenQuotedArgWithEscapedQuote_whenParse_thenEscapesResolved() {
        String content = "create(\"a \\\"quoted\\\" name\");";

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(List.of("a \"quoted\" name"), ops.get(0).args());
    }

    @Test
    public void givenEmptyContent_whenParse_thenEmptyList() {
        assertTrue(parser.parse("", "test.txt").isEmpty());
    }

    @Test
    public void givenNullContent_whenParse_thenEmptyList() {
        assertTrue(parser.parse((String) null, "test.txt").isEmpty());
    }

    @Test
    public void givenWhitespaceAndCommentOnlyContent_whenParse_thenEmptyList() {
        String content = """

                # just a comment
                // and another

                """;

        assertTrue(parser.parse(content, "test.txt").isEmpty());
    }

    @Test
    public void givenUnterminatedCallMissingSemicolon_whenParse_thenThrows(CapturedOutput output) {
        String content = "login(rina, pw)";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(ex.getMessage().contains("';'"));
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:1: missing ';'"));
    }

    @Test
    public void givenMissingClosingParen_whenParse_thenThrows(CapturedOutput output) {
        String content = "login(rina, pw;";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:1: unterminated operation (missing ')')"));
    }

    @Test
    public void givenMissingOpeningParen_whenParse_thenThrows(CapturedOutput output) {
        String content = "login;";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(ex.getMessage().contains("'('"));
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:1: missing '('"));
    }

    @Test
    public void givenUnbalancedQuote_whenParse_thenThrows(CapturedOutput output) {
        String content = "login(\"rina, pw);";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:1: unbalanced quote"));
    }

    @Test
    public void givenMissingOperationName_whenParse_thenThrows(CapturedOutput output) {
        String content = "(rina);";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:1: missing operation name"));
    }

    @Test
    public void givenMalformedInput_whenParse_thenMessageIncludesLineNumber(CapturedOutput output) {
        String content = """
                login(rina, pw);
                open(broken""";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content, "test.txt"));
        assertTrue(ex.getMessage().contains("line 2"),
                "expected line number in message but was: " + ex.getMessage());
        assertTrue(output.getOut().contains("[PARSE ERROR] test.txt:2: unterminated operation"));
    }

    @Test
    public void givenQuotedArgContainingSlashSlash_whenParse_thenNotTreatedAsComment() {
        String content = "navigate(\"https://example.com/path\");";

        List<InitialStateOperation> ops = parser.parse(content, "test.txt");

        assertEquals(List.of("https://example.com/path"), ops.get(0).args());
    }
}
