/* Copyright (c) 2013, University of Oslo, Norway
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
package org.vortikal.util.text;

import static junit.framework.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SimpleTemplateTest {

    @Test
    public void test() {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("foo", "bar");
        
        String result = render("var: ${foo}", vars);
        assertEquals(result, "var: bar");

        vars.put("url", "http://example.com");
        result = render("url: %{url}", vars, "%{", "}");
        assertEquals(result, "url: http://example.com");
        
        vars.put("foo:bar",  "resolved");
        result = render("%{foo:bar}", vars, "%{", "}");
        assertEquals(result, "resolved");
        
        vars.put("xxx", "yyy");
        result = render("{$xxx}", vars, "{$", "}");
        assertEquals(result, "yyy");
        
        assertEquals("no placeholders", render("no placeholders", vars, "%{", "}"));

        assertEquals("empty placeholder", render("empty %{}placeholder", vars, "%{", "}"));

        assertEquals("escaped ${foo} \\", render("escaped \\${foo} \\\\", vars, "${", "}"));
        
        vars.put("xxx}", "strange");
        assertEquals("escaped suffix strange", render("escaped suffix ${xxx\\}}", vars, "${", "}"));

        assertEquals("escaped suffix not closing ${placeholder}", render("escaped suffix not closing ${placeholder\\}", vars, "${", "}"));
        
        assertEquals("invalid } stuff ${foo:bar", render("invalid } stuff ${foo:bar", vars, "${", "}"));
        
        assertEquals("x } x", render("x } x", vars, "${", "}"));
        
        assertEquals("} is not supported", render("${placeholder in ${foo}} is not supported", vars, "${", "}"));
        
        assertEquals("Foo is bar, same prefix and suffix delimiter", render("Foo is |foo|, same prefix and suffix delimiter", vars, "|", "|"));

        assertEquals("Foo is |foo|", render("Foo is \\|foo\\|", vars, "|", "|"));

        assertEquals("${", render("${", vars, "${", "}"));
        
        assertEquals("}", render("}", vars, "${", "}"));
        
        assertEquals("x", render("x", vars, "%{", "}"));

        assertEquals("", render("\\", vars, "%{", "}"));
        
        assertEquals("", render("", vars, "%{", "}"));
    }
    
    @Test
    public void testEscapingParseFlags() {
        Map<String, String> vars = new HashMap<String, String>();
        vars.put("foo", "bar");
        vars.put("\\", "ESCCHAR");
        vars.put("\\\\", "DOUBLE_ESCCHAR");
        vars.put("${", "PREFIX");
        vars.put("}", "SUFFIX");
        vars.put("\\}", "ESC_SUFFIX");
        vars.put("\\${", "ESC_PREFIX");
        
        String testTemplate =                 "${foo} \\\\ \\x \\${foo} ${${} ${\\}} ${\\${} \\${\\\\}\\ ${\\}";
        
        String expectDefault =                "bar \\ x ${foo} PREFIX SUFFIX PREFIX ${\\} ${}";
        String expectNoEscapeHandling =       "bar \\\\ \\x \\bar PREFIX ESCCHAR} ESC_PREFIX \\DOUBLE_ESCCHAR\\ ESCCHAR";
        String expectKeepAllEscapeChars =     "bar \\\\ \\x \\${foo} PREFIX ESC_SUFFIX ESC_PREFIX \\${\\\\}\\ ${\\}";
        String expectKeepInvalidEscapeChars = "bar \\ \\x ${foo} PREFIX SUFFIX ESC_PREFIX ${\\}\\ ${}";
        
        assertEquals(expectDefault, render(testTemplate, vars, 0));
        
        assertEquals(expectNoEscapeHandling, render(testTemplate, vars, SimpleTemplate.NO_ESCAPE_HANDLING));

        assertEquals(expectKeepAllEscapeChars, render(testTemplate, vars, SimpleTemplate.KEEP_ALL_ESCAPE_CHARS));

        assertEquals(expectKeepInvalidEscapeChars, render(testTemplate, vars, SimpleTemplate.KEEP_INVALID_ESCAPE_CHARS));
        
        // KEEP_ALL_ESCAPE_CHARS overrides KEEP_INVALID_ESCAPE_CHARS:
        assertEquals(expectKeepAllEscapeChars, render(testTemplate, vars, SimpleTemplate.KEEP_INVALID_ESCAPE_CHARS
                                                               | SimpleTemplate.KEEP_ALL_ESCAPE_CHARS));

        // NO_ESCAPE_HANDLING overrides any other flag
        assertEquals(expectNoEscapeHandling, render(testTemplate, vars, SimpleTemplate.NO_ESCAPE_HANDLING
                                                               | SimpleTemplate.KEEP_INVALID_ESCAPE_CHARS
                                                               | SimpleTemplate.KEEP_ALL_ESCAPE_CHARS));
        
        assertEquals(expectNoEscapeHandling, render(testTemplate, vars, SimpleTemplate.NO_ESCAPE_HANDLING
                                                               | SimpleTemplate.KEEP_ALL_ESCAPE_CHARS));
        
        assertEquals(expectNoEscapeHandling, render(testTemplate, vars, SimpleTemplate.NO_ESCAPE_HANDLING
                                                               | SimpleTemplate.KEEP_INVALID_ESCAPE_CHARS));
    }
    
    @Test
    public void testNoEscapeHandlingQueryExpression() {
        Map<String,String> vars = new HashMap<String,String>();
        vars.put("currentFolder", "/query-tests/a\\ folder\\ with\\ spaces");
        
        String testTemplate = "(uri = {$currentFolder}* AND type IN file) OR uri = /query-tests/a\\ folder\\ with\\ spaces";
        
        assertEquals("(uri = /query-tests/a\\ folder\\ with\\ spaces* AND type IN file) OR uri = /query-tests/a\\ folder\\ with\\ spaces",
                render(testTemplate, vars, "{$", "}", SimpleTemplate.NO_ESCAPE_HANDLING));
    }

    private String render(String template, Map<String, String> vars) {
        return render(template, vars, "${", "}", 0);
    }
    
    private String render(String template, Map<String, String> vars, int flags) {
        return render(template, vars, "${", "}", flags);
    }
    
    private String render(String template, final Map<String, String> vars, 
                          String delimPrefix, String delimSuffix) {
        return render(template, vars, delimPrefix, delimSuffix, 0);
    }
    private String render(String template, final Map<String, String> vars, 
                          String delimPrefix, String delimSuffix, int flags) {
        final StringBuilder result = new StringBuilder();
        SimpleTemplate t = SimpleTemplate.compile(template, delimPrefix, delimSuffix, flags);
        t.apply(new SimpleTemplate.Handler() {
            @Override
            public void write(String text) {
                result.append(text);
            }
            @Override
            public String resolve(String variable) {
                if (vars.containsKey(variable)) {
                    return vars.get(variable);
                }
                return "";
            }
        });
        return result.toString();
    }
    
}
