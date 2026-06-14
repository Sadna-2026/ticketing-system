package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class InitialStateParserTest {

    private final InitialStateParser parser = new InitialStateParser();

    @Test
    public void givenMultipleOperations_whenParse_thenReturnsThemInOrderWithNamesAndArgs() {
        String content = """
                login(rina, pw);
                open-production-company(rina_token, "Demo Co", "A demo company");
                """;

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(2, ops.size());
        assertEquals("login", ops.get(0).name());
        assertEquals(List.of("rina", "pw"), ops.get(0).args());
        assertEquals("open-production-company", ops.get(1).name());
        assertEquals(List.of("rina_token", "Demo Co", "A demo company"), ops.get(1).args());
    }

    @Test
    public void givenQuotedArgWithCommaAndSpaces_whenParse_thenOneArgWithQuotesStripped() {
        String content = "create(\"Hello, world; and more\");";

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(1, ops.size());
        assertEquals(List.of("Hello, world; and more"), ops.get(0).args());
    }

    @Test
    public void givenUnquotedArgs_whenParse_thenTrimmed() {
        String content = "op(  a  ,   b  );";

        List<InitialStateOperation> ops = parser.parse(content);

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

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(2, ops.size());
        assertEquals("login", ops.get(0).name());
        assertEquals("logout", ops.get(1).name());
    }

    @Test
    public void givenZeroArgCall_whenParse_thenEmptyArgList() {
        String content = "refresh();";

        List<InitialStateOperation> ops = parser.parse(content);

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

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(1, ops.size());
        assertEquals("open-production-company", ops.get(0).name());
        assertEquals(List.of("rina_token", "Demo Co", "A demo company"), ops.get(0).args());
    }

    @Test
    public void givenQuotedArgWithEscapedQuote_whenParse_thenEscapesResolved() {
        String content = "create(\"a \\\"quoted\\\" name\");";

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(List.of("a \"quoted\" name"), ops.get(0).args());
    }

    @Test
    public void givenEmptyContent_whenParse_thenEmptyList() {
        assertTrue(parser.parse("").isEmpty());
    }

    @Test
    public void givenNullContent_whenParse_thenEmptyList() {
        assertTrue(parser.parse((String) null).isEmpty());
    }

    @Test
    public void givenWhitespaceAndCommentOnlyContent_whenParse_thenEmptyList() {
        String content = """

                # just a comment
                // and another

                """;

        assertTrue(parser.parse(content).isEmpty());
    }

    @Test
    public void givenUnterminatedCallMissingSemicolon_whenParse_thenThrows() {
        String content = "login(rina, pw)";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("';'"));
    }

    @Test
    public void givenMissingClosingParen_whenParse_thenThrows() {
        String content = "login(rina, pw;";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content));
    }

    @Test
    public void givenMissingOpeningParen_whenParse_thenThrows() {
        String content = "login;";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("'('"));
    }

    @Test
    public void givenUnbalancedQuote_whenParse_thenThrows() {
        String content = "login(\"rina, pw);";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content));
    }

    @Test
    public void givenMissingOperationName_whenParse_thenThrows() {
        String content = "(rina);";

        assertThrows(InitialStateParseException.class, () -> parser.parse(content));
    }

    @Test
    public void givenMalformedInput_whenParse_thenMessageIncludesLineNumber() {
        String content = """
                login(rina, pw);
                open(broken""";

        InitialStateParseException ex =
                assertThrows(InitialStateParseException.class, () -> parser.parse(content));
        assertTrue(ex.getMessage().contains("line 2"),
                "expected line number in message but was: " + ex.getMessage());
    }

    @Test
    public void givenQuotedArgContainingSlashSlash_whenParse_thenNotTreatedAsComment() {
        String content = "navigate(\"https://example.com/path\");";

        List<InitialStateOperation> ops = parser.parse(content);

        assertEquals(List.of("https://example.com/path"), ops.get(0).args());
    }
}
