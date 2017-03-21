/* Copyright (c) 2009, 2015, University of Oslo, Norway
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 
 *  * Neither the name of the University of Oslo nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *      
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package vtk.util.text;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class TextUtilsTest {

    @Test
    public void testTokenizeWithPhrases() {

        testTokenizeWithPhrases_expectTokens("a", "a");
        testTokenizeWithPhrases_expectTokens("a b c", "a", "b", "c");
        testTokenizeWithPhrases_expectTokens("'some phrase' here", "some phrase", "here");
        testTokenizeWithPhrases_expectTokens("'some phrase ' here", "some phrase ", "here");

        testTokenizeWithPhrases_expectTokens("'\"a dquote inside squotes\"' \"'an squote inside dquotes'\"",
                                             "\"a dquote inside squotes\"", "'an squote inside dquotes'");

        testTokenizeWithPhrases_expectTokens("foo 'an unterminated squote phrase", "foo", "an unterminated squote phrase");

        testTokenizeWithPhrases_expectTokens("x'phrase'", "xphrase");
        testTokenizeWithPhrases_expectTokens("'phrase'y", "phrasey");
        testTokenizeWithPhrases_expectTokens("x'phrase'y", "xphrasey");
        testTokenizeWithPhrases_expectTokens("x 'phrase'y", "x", "phrasey");
        testTokenizeWithPhrases_expectTokens("x'phrase' y", "xphrase", "y");
        testTokenizeWithPhrases_expectTokens("x\"phrase\"", "xphrase");
        testTokenizeWithPhrases_expectTokens("\"phrase\"y", "phrasey");
        testTokenizeWithPhrases_expectTokens("x \"phrase\"y", "x","phrasey");
        testTokenizeWithPhrases_expectTokens("x\"phrase\" y", "xphrase","y");

        testTokenizeWithPhrases_expectTokens(" 'two become'' one' ", "two become one");
        testTokenizeWithPhrases_expectTokens(" \"two become\"' one' ", "two become one");
        testTokenizeWithPhrases_expectTokens(" 'two become'\" one\" ", "two become one");
        testTokenizeWithPhrases_expectTokens(" \"two become\"\" one\" ", "two become one");

        // Cornerish cases
        testTokenizeWithPhrases_expectTokens("", new String[0]);
        testTokenizeWithPhrases_expectTokens("'", new String[0]);
        testTokenizeWithPhrases_expectTokens("''", new String[0]);
        testTokenizeWithPhrases_expectTokens("''''", new String[0]);
        testTokenizeWithPhrases_expectTokens("'' ''", new String[0]);
        testTokenizeWithPhrases_expectTokens("\"", new String[0]);
        testTokenizeWithPhrases_expectTokens("\"\"", new String[0]);
        testTokenizeWithPhrases_expectTokens("\\\\", "\\");
        testTokenizeWithPhrases_expectTokens("           ", new String[0]);
        testTokenizeWithPhrases_expectTokens("     x      ", "x");
        testTokenizeWithPhrases_expectTokens("    ' x '     ", " x ");
        testTokenizeWithPhrases_expectTokens("\\  \\ ", " ", " ");
        testTokenizeWithPhrases_expectTokens("'\\x\\''", "x'");
        testTokenizeWithPhrases_expectTokens("\"\\x\\\"\"", "x\"");
    }

    private void testTokenizeWithPhrases_expectTokens(String text, String...expectedTokens) {
        List<String> tokens = TextUtils.tokenizeWithPhrases(text);
        assertEquals(expectedTokens.length, tokens.size());
        for (int i=0; i<expectedTokens.length; i++) {
            assertEquals(expectedTokens[i], tokens.get(i));
        }
    }

    @Test
    public void testRemoveDuplicatesIgnoreCase() {

        // OLD: String testData = "Forskning, Røed Ødegård, forskning, FoRsKning, forskNING";
        String testData = "oFFentlig helseVesen, jon blund, Offentlig helsevesen, Jon Blund";

        String expectedData = "oFFentlig helseVesen, jon blund";
        String expectedDataNoSpaces = "oFFentlig helseVesen,jon blund";
        String expectedDataNoDelimiter = "oFFentlig helseVesen jon blund";
        String expectedDataNoSpacesAndDelimiter = "oFFentlig helseVesenjon blund";
        String expectedDataCapitalized = "OFFentlig HelseVesen, Jon Blund";

        // Test noSpaces, noDelimiter boolean configurations
        String test = TextUtils.removeDuplicatesIgnoreCase(testData, ",", false, false, false);
        assertEquals(expectedData, test);

        test = TextUtils.removeDuplicatesIgnoreCase(testData, ",", true, false, false);
        assertEquals(expectedDataNoSpaces, test);

        test = TextUtils.removeDuplicatesIgnoreCase(testData, ",", false, true, false);
        assertEquals(expectedDataNoDelimiter, test);

        test = TextUtils.removeDuplicatesIgnoreCase(testData, ",", true, true, false);
        assertEquals(expectedDataNoSpacesAndDelimiter, test);

        // Overload Methods
        test = TextUtils.removeDuplicatesIgnoreCase(testData, ",");
        assertEquals(expectedData, test);

        test = TextUtils.removeDuplicatesIgnoreCaseCapitalized(testData, ",");
        assertEquals(expectedDataCapitalized, test);

    }

    @Test
    public void testParseCsv() {
        
        String[] values = TextUtils.parseCsv("a,b,c", ',');
        assertEquals(3, values.length);
        assertEquals("a", values[0]);
        assertEquals("b", values[1]);
        assertEquals("c", values[2]);
        
        values = TextUtils.parseCsv(" First ; Second ; Third ;", ';');
        assertEquals(4, values.length);
        assertEquals(" First ", values[0]);
        assertEquals(" Second ", values[1]);
        assertEquals(" Third ", values[2]);
        assertEquals("", values[3]);
        
        values = TextUtils.parseCsv(" First ; Second ; Third ;", ';', TextUtils.DISCARD);
        assertEquals(3, values.length);
        assertEquals(" First ", values[0]);
        assertEquals(" Second ", values[1]);
        assertEquals(" Third ", values[2]);

        values = TextUtils.parseCsv(" First ; Second ; Third ;", ';', TextUtils.TRIM|TextUtils.DISCARD);
        assertEquals(3, values.length);
        assertEquals("First", values[0]);
        assertEquals("Second", values[1]);
        assertEquals("Third", values[2]);
        
        values = TextUtils.parseCsv("", ';');
        assertEquals(1, values.length);
        assertEquals("", values[0]);

        values = TextUtils.parseCsv("", ';', TextUtils.DISCARD);
        assertEquals(0, values.length);
        
        values = TextUtils.parseCsv(";;;;a value;;;;", ';', TextUtils.DISCARD);
        assertEquals(1, values.length);
        assertEquals("a value", values[0]);

        values = TextUtils.parseCsv("  ;a value;  ", ';', TextUtils.TRIM);
        assertEquals(3, values.length);
        assertEquals("", values[0]);
        assertEquals("a value", values[1]);
        assertEquals("", values[2]);
        
        values = TextUtils.parseCsv("value\\;a;value\\;b;value\\\\c", ';');
        assertEquals(3, values.length);
        assertEquals("value;a", values[0]);
        assertEquals("value;b", values[1]);
        assertEquals("value\\c", values[2]);
        
        values = TextUtils.parseCsv("1 \t2 \t3 ", '\t');
        assertEquals(3, values.length);
        assertEquals("1 ", values[0]);
        assertEquals("2 ", values[1]);
        assertEquals("3 ", values[2]);
        
        values = TextUtils.parseCsv("1 \t2 \t3 ", '\t', TextUtils.TRIM);
        assertEquals(3, values.length);
        assertEquals("1", values[0]);
        assertEquals("2", values[1]);
        assertEquals("3", values[2]);
        
        try {
            TextUtils.parseCsv("value\\a;value\\b", ';');
            fail("Expected invalid escape exception");
        } catch (IllegalArgumentException ia) {
            // ok
        }
        
        try {
            values = TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.IGNORE_INVALID_ESCAPE);
            assertEquals(2, values.length);
            assertEquals("\\value\\a", values[0]);
            assertEquals("\\value\\b", values[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }
        
        try {
            values = TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.UNESCAPE_INVALID_ESCAPE);
            assertEquals(2, values.length);
            assertEquals("\\valuea", values[0]);
            assertEquals("\\valueb", values[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }

        // Test that flag TextUtils.IGNORE_INVALID_ESCAPE makes no difference when
        // already using flag TextUtils.UNESCAPE_INVALID_ESCAPE
        assertEquals(TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.UNESCAPE_INVALID_ESCAPE)[0],
                     TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.UNESCAPE_INVALID_ESCAPE | TextUtils.IGNORE_INVALID_ESCAPE)[0]);
        assertEquals(TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.UNESCAPE_INVALID_ESCAPE)[1],
                     TextUtils.parseCsv("\\\\value\\a;\\\\value\\b", ';', TextUtils.UNESCAPE_INVALID_ESCAPE | TextUtils.IGNORE_INVALID_ESCAPE)[1]);
        
        try {
            TextUtils.parseCsv("value a;value b\\", ';');
            fail("Expected invalid escape exception");
        } catch (IllegalArgumentException ia) {
            // ok
        }
        
        try {
            values = TextUtils.parseCsv("value a;value b\\", ';', TextUtils.IGNORE_INVALID_ESCAPE);
            assertEquals(2, values.length);
            assertEquals("value a", values[0]);
            assertEquals("value b", values[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }
        
        // Test when input only consists of single backslash, or ends with one.
        try {
            values = TextUtils.parseCsv("\\", ',');
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException ia) {
            // oK
        }

        values = TextUtils.parseCsv("\\", ',', TextUtils.IGNORE_INVALID_ESCAPE);
        assertEquals(0, values.length);
        
        values = TextUtils.parseCsv("\\", ',', TextUtils.UNESCAPE_INVALID_ESCAPE);
        assertEquals(0, values.length);
    }
    
    @Test
    public void testParseKeyValue() {
        String[] kv = TextUtils.parseKeyValue("foo:bar", ':');
        assertEquals(2, kv.length);
        assertEquals("foo", kv[0]);
        assertEquals("bar", kv[1]);
        
        kv = TextUtils.parseKeyValue("foobar:", ':');
        assertEquals(2, kv.length);
        assertEquals("foobar", kv[0]);
        assertEquals("", kv[1]);
        
        kv = TextUtils.parseKeyValue(":foobar", ':');
        assertEquals(2, kv.length);
        assertEquals("", kv[0]);
        assertEquals("foobar", kv[1]);
        
        kv = TextUtils.parseKeyValue(":", ':');
        assertEquals(2, kv.length);
        assertEquals("", kv[0]);
        assertEquals("", kv[1]);
        
        kv = TextUtils.parseKeyValue("", ':');
        assertEquals(2, kv.length);
        assertEquals("", kv[0]);
        assertNull(kv[1]);
        
        kv = TextUtils.parseKeyValue("foobar", ':');
        assertEquals(2, kv.length);
        assertEquals("foobar", kv[0]);
        assertNull(kv[1]);
        
        kv = TextUtils.parseKeyValue("foo\\:bar:baz\\\\Z", ':');
        assertEquals(2, kv.length);
        assertEquals("foo:bar", kv[0]);
        assertEquals("baz\\Z", kv[1]);
        
        kv = TextUtils.parseKeyValue("  a key  \t=  val\t", '=', TextUtils.TRIM);
        assertEquals(2, kv.length);
        assertEquals("a key", kv[0]);
        assertEquals("val", kv[1]);

        kv = TextUtils.parseKeyValue("  a key  \t=  val\t", '=');
        assertEquals(2, kv.length);
        assertEquals("  a key  \t", kv[0]);
        assertEquals("  val\t", kv[1]);
        
        kv = TextUtils.parseKeyValue("  a key  \t=  \t", '=', TextUtils.TRIM);
        assertEquals(2, kv.length);
        assertEquals("a key", kv[0]);
        assertEquals("", kv[1]);
        
        try {
            TextUtils.parseKeyValue("\\a=b", '=');
            fail("Expected invalid escape exception");
        } catch (IllegalArgumentException ia) {
            // ok
        }
        
        try {
            kv = TextUtils.parseKeyValue("\\a=b", '=', TextUtils.IGNORE_INVALID_ESCAPE);
            assertEquals(2, kv.length);
            assertEquals("\\a", kv[0]);
            assertEquals("b", kv[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }
        
        try {
            TextUtils.parseKeyValue("a=b\\", '=', TextUtils.TRIM);
            fail("Expected invalid escape exception");
        } catch (IllegalArgumentException ia) {
            // ok
        }
        
        try {
            kv = TextUtils.parseKeyValue("a= \\b\\ ", '=', TextUtils.TRIM | TextUtils.IGNORE_INVALID_ESCAPE);
            assertEquals(2, kv.length);
            assertEquals("a", kv[0]);
            assertEquals("\\b\\", kv[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }
        
        try {
            kv = TextUtils.parseKeyValue("a= \\b\\ ", '=', TextUtils.TRIM | TextUtils.UNESCAPE_INVALID_ESCAPE);
            assertEquals(2, kv.length);
            assertEquals("a", kv[0]);
            assertEquals("b", kv[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid escape exception");
        }

        // Test that flag TextUtils.IGNORE_INVALID_ESCAPE makes no difference when
        // already using flag TextUtils.UNESCAPE_INVALID_ESCAPE
        assertEquals(TextUtils.parseKeyValue("a=\\b", '=', TextUtils.UNESCAPE_INVALID_ESCAPE)[0],
                     TextUtils.parseKeyValue("a=\\b", '=', TextUtils.IGNORE_INVALID_ESCAPE | TextUtils.UNESCAPE_INVALID_ESCAPE)[0]);
        assertEquals(TextUtils.parseKeyValue("a=\\b", '=', TextUtils.UNESCAPE_INVALID_ESCAPE)[1],
                     TextUtils.parseKeyValue("a=\\b", '=', TextUtils.IGNORE_INVALID_ESCAPE | TextUtils.UNESCAPE_INVALID_ESCAPE)[1]);
        
        try {
            kv = TextUtils.parseKeyValue("a = b = c = d", '=', TextUtils.IGNORE_UNESCAPED_SEP_IN_VALUE | TextUtils.TRIM);
            assertEquals(2, kv.length);
            assertEquals("a", kv[0]);
            assertEquals("b = c = d", kv[1]);
        } catch (IllegalArgumentException ia) {
            fail("Unexpected invalid argument exception: " + ia.getMessage());
        }
    }
    
    @Test
    public void testDeleteAll() {
        assertEquals("", TextUtils.deleteAll("xxx", "x"));
        assertEquals("a", TextUtils.deleteAll("abcd", "bcd"));
        assertEquals("d", TextUtils.deleteAll("abcd", "abc"));
        assertEquals("bc", TextUtils.deleteAll(TextUtils.deleteAll("abcd", "a"),"d"));
        assertEquals("abcd", TextUtils.deleteAll("abcd", ""));
    }
    
    @Test
    public void testReplaceAll() {
        assertEquals("abc", TextUtils.replaceAll("aBBAc", "BBA", "b"));
        assertEquals("aBBAc", TextUtils.replaceAll("abc", "b", "BBA"));
        assertEquals("a", TextUtils.replaceAll("XXXXXXXXXXXXXXXa", "X", ""));
        assertEquals("aX", TextUtils.replaceAll("aXXX", "XX", ""));
        assertEquals("xyz", TextUtils.replaceAll("xyzabc", "abc", ""));
        assertEquals("PREFIX-", TextUtils.replaceAll("PREFIX-MIDDLE-SUFFIX", "MIDDLE-SUFFIX", ""));
        assertEquals("MIDDLE",  TextUtils.replaceAll(
                                   TextUtils.replaceAll("PREFIX-MIDDLE-SUFFIX", "-SUFFIX", ""), "PREFIX-", ""));
        assertEquals("1 bar 2 bar 3", TextUtils.replaceAll("1 foo 2 foo 3", "foo", "bar"));
        assertEquals("abababa", TextUtils.replaceAll("bbb", "", "a"));
        assertEquals("unchanged", TextUtils.replaceAll("unchanged", "X", "Y"));
        assertEquals("abc", TextUtils.replaceAll("abc", "abigreplacement", "X"));
        assertEquals("|-|-|-|-|-|-|-|", TextUtils.replaceAll("|.|.|.|.|.|.|.|", ".", "-"));
        assertEquals("-.-.-.-.-.-.-.-", TextUtils.replaceAll("|.|.|.|.|.|.|.|", "|", "-"));
        assertEquals("", TextUtils.replaceAll("XXXXXXXXXXXXXXX", "X", ""));
        assertEquals("YXY", TextUtils.replaceAll("X", "", "Y"));
        assertEquals("Y", TextUtils.replaceAll("", "", "Y"));
        assertEquals("", TextUtils.replaceAll("", "X", "Y"));
        assertEquals("", TextUtils.replaceAll("", "", ""));

        // Test equal behaviour to that of String.replaceAll, when what to replace is not a regexp:
        assertEqualsStringReplaceAll("aBBAc", "BBA", "b");
        assertEqualsStringReplaceAll("abc", "b", "BBA");
        assertEqualsStringReplaceAll("XXXXXXXXXXXXXXXa", "X", "");
        assertEqualsStringReplaceAll("PREFIX-MIDDLE-SUFFIX", "MIDDLE-SUFFIX", "");
        assertEqualsStringReplaceAll("1 foo 2 foo 3", "foo", "bar");
        assertEqualsStringReplaceAll("bbb", "", "a");
        assertEqualsStringReplaceAll("aaa", "aa", "b");
        assertEqualsStringReplaceAll("unchanged", "X", "Y");
        assertEqualsStringReplaceAll("abc", "abigreplacement", "X");
        assertEqualsStringReplaceAll("|.|.|.|.|.|.|.|", ".", "-");
        assertEqualsStringReplaceAll("|.|.|.|.|.|.|.|", "|", "-");
        assertEqualsStringReplaceAll("XXXXXXXXXXXXXXX", "X", "");
        assertEqualsStringReplaceAll("X", "", "Y");
        assertEqualsStringReplaceAll("", "", "Y");
        assertEqualsStringReplaceAll("", "X", "Y");
        assertEqualsStringReplaceAll("", "", "");
    }
    
    private void assertEqualsStringReplaceAll(String input, String replace, String replacement) {
        assertEquals(TextUtils.replaceAll(input, replace, replacement),
                     input.replaceAll(Pattern.quote(replace), Matcher.quoteReplacement(replacement)));
    }
    
    @Test
    public void testUnescape() {
        assertEquals("a b c", TextUtils.unescape("\\a \\b c"));
        assertEquals("a b c", TextUtils.unescape("\\a \\b c\\"));
        assertEquals("", TextUtils.unescape(""));
        assertEquals("", TextUtils.unescape("\\"));
        assertEquals(" ", TextUtils.unescape("\\ "));
        assertEquals("a \\ b", TextUtils.unescape("a \\\\ b"));
    }
    
    @Test
    public void testJoinEmpty() {
        String[] strings = {};
        assertEquals("join result", "", TextUtils.join(strings, ", "));
    }
    
    @Test
    public void toUnicodeEscape() {
        assertEquals("\\u0000", TextUtils.toUnicodeEscape((char)0));
        assertEquals("\\u007f", TextUtils.toUnicodeEscape((char)0x7f));
        assertEquals("\\u2000", TextUtils.toUnicodeEscape('\u2000'));
    }

    @Test
    public void testJoinSingleDoesNotUseSeparator() {
        String[] strings = {"one"};
        assertEquals("join result", "one", TextUtils.join(strings, ", "));
    }

    @Test
    public void testJoinMultipleUseSeparator() {
        String[] strings = {"one", "two"};
        assertEquals("join result", "one, two", TextUtils.join(strings, ", "));
    }

    @Test
    public void testJoinEmptyStringsMayBeIncluded() {
        String[] strings = {"", "one", ""};
        assertEquals("join result", ", one, ", TextUtils.join(strings, ", "));
    }

    @Test
    public void testJoinEmptyStringsMayBeSkipped() {
        String[] strings = {"", "one", ""};
        assertEquals("join result", "one", TextUtils.join(strings, ", ", true));
    }

    @Test
    public void testJoinNullsMayBeSkipped() {
        String[] strings = {"one", null, "two"};
        assertEquals("join result", "one, two", TextUtils.join(strings, ", ", true, true));
    }

    @Test(expected=java.lang.NullPointerException.class)
    public void testJoinUnsolicitedNullsCauseFailure() {
        String[] strings = {"one", null, "two"};
        assertEquals("join result", "", TextUtils.join(strings, ", ", true, false));
    }

    @Test
    public void testJoinList() {
        List<String> strings = new ArrayList<String>();
        strings.add("one");
        strings.add("two");
        assertEquals("join result", "one, two", TextUtils.join(strings, ", "));
    }

    @Test
    public void testRemoveUnprintablesWhenContentContainsControlCharactersThenUnknownShouldBeRemoved() throws Exception {
        String result = TextUtils.removeUnprintables("back \b space");
        assertEquals("should not contain back space", -1, result.indexOf('\b')); 
    }

    @Test
    public void testRemoveUnprintablesWhenContentContainsControlCharactersThenUnknownShouldBeRemoved2() throws Exception {
        String result = TextUtils.removeUnprintables("some \u000btext");
        assertEquals("should not contain control character", "some text", result); 
    }

    @Test
    public void testRemoveUnprintablesWhenContentContainsLocalizedQuotesThenShouldBeAcceptedSinceTheyAreValidUnicode() throws Exception {
        String text = "\u201C some text \u201D";
        String result = TextUtils.removeUnprintables(text);
        assertEquals("text should be unchanged", text, result); 
    }

}
