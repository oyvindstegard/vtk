/* Copyright (c) 2004, University of Oslo, Norway
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * EvenStructuredText *sigh...*
 * 
 * @author Even Halvorsen & Kristian Syversen
 *         (based on guru Gorm A. Paulsen's previous version
 *         DefaultStructuredText.java)
 * 
 * This class contains methods for generating XML-elements from structured text
 * and methods for generating structured text from an XML-structure.
 */
public final class EvenStructuredText implements StructuredText {
    
    public void setTextMappings(Map<String, String> customMappings) {
        this.tagNames = customMappings;
    }
    
    protected String LINE_SEPARATOR = "\n";
    protected String SPACE = " ";
    protected String LISTITEM_MARKER = "-";
    protected String LISTITEM_START = this.LINE_SEPARATOR + this.LISTITEM_MARKER + this.SPACE;
    protected String NUMLISTITEM_MARKER = "#";
    protected String NUMLISTITEM_START = this.LINE_SEPARATOR + this.NUMLISTITEM_MARKER + this.SPACE;
    protected String LIST_START = this.LISTITEM_START;
    protected String LIST_CLOSE = this.LINE_SEPARATOR + this.LINE_SEPARATOR;
    protected String NUMLIST_START = this.NUMLISTITEM_START;
    protected String NUMLIST_CLOSE = this.LINE_SEPARATOR + this.LINE_SEPARATOR;
    protected String PARAGRAPH_START = this.LINE_SEPARATOR + this.LINE_SEPARATOR;
    protected String PARAGRAPH_CLOSE = this.LINE_SEPARATOR + this.LINE_SEPARATOR;
    protected String BOLD_START = "*";
    protected String BOLD_CLOSE = "*";
    protected String ITALIC_START = "_";
    protected String ITALIC_CLOSE = "_";
    protected String LINK_START = "\"";
    protected String LINK_MIDDLE = "\":";
    protected String REF_START = "[";
    protected String REF_ATTRIBUTE = ":";
    protected String REF_CLOSE = "]";
    protected String SUB_START = "[sub:";
    protected String SUB_END = "]";
    protected String SUPER_START = "[super:";
    protected String SUPER_END = "]";
    protected char NEWLINE = '\n';
    protected char ESCAPE = '\\';

    protected Map<String, String> tagNames = getEnglishTagNames();
    
    
    public Set<String> getTagNames() {
        return this.tagNames.keySet();
    }
    
    
    /*
    protected boolean escapeAtPos(String text, int pos) {
        return (text.indexOf(this.ESCAPE, pos) == pos);
    }
    */
    
        
    protected boolean paragraphAtPos(String text, int pos) {
        return (text.indexOf(this.PARAGRAPH_START, pos) == pos);
    }
    
        
    // test that current char is NEWLINE which i NOT followed
    // by another NEWLINE (as newline + newline = paragraph)
    protected boolean newlineAtPos(String text, int pos) {
        /**
         * FIXME
         * Hvis vi skal ha mulighet for escaping av newline skal sjekk for escape legges inn igjen
         */
        if (pos < text.length() - 1
                && text.charAt(pos) == this.NEWLINE
                && text.charAt(pos + 1) != this.NEWLINE )
                //&& (pos > 0 && text.charAt(pos-1) != this.ESCAPE)) 
            return true;
        return false;
    }
    
    
    protected boolean listAtPos(String text, int pos) {
       /*
        if (pos > 0 && this.ESCAPE == text.charAt(pos-1))
           return false;
           */
        return (text.indexOf(this.LIST_START, pos) == pos);
    }
    
    
    protected boolean numlistAtPos(String text, int pos) {
       /*
        if (pos > 0 && this.ESCAPE == text.charAt(pos-1))
           return false;
           */
        return (text.indexOf(this.NUMLIST_START, pos) == pos);
    }
    
    
    protected boolean listitemAtPos(String text, int pos) {
        /**
         * FIXME: Test om man må forutsette at man har en korrekt start på liste
         */
        if (pos > 0 && this.ESCAPE == text.charAt(pos-1))
            return false;
        return (text.indexOf(this.LISTITEM_START, pos) == pos);
    }
    
    
    protected boolean numlistitemAtPos(String text, int pos) {
        /**
         * FIXME: Samme som forrige metode
         */
        if (pos > 0 && this.ESCAPE == text.charAt(pos-1))
            return false;
        return (text.indexOf(this.NUMLISTITEM_START, pos) == pos);
    }
    
    
    protected boolean boldAtPos(String text, int pos) {
        if (text.indexOf(this.BOLD_START, pos) != pos)
            return false;
        
        if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
            return false;
        
        int endPos = text.indexOf(this.BOLD_CLOSE, pos + this.BOLD_START.length());
        endPos = findTrueEndPos(text, endPos, this.BOLD_CLOSE);
        
        if (endPos > 0)
            return !(endOfBlockLevelElement(text, pos + this.BOLD_START.length(),
                    endPos));

        return false;
    }
    
    
    protected boolean italicAtPos(String text, int pos) {
        if (text.indexOf(this.ITALIC_START, pos) != pos)
            return false;

        if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
            return false;
        
        int endPos = text.indexOf(this.ITALIC_CLOSE, pos + this.ITALIC_START.length());
        endPos = findTrueEndPos(text, endPos, this.ITALIC_CLOSE);

        if (endPos > 0)
            return !(endOfBlockLevelElement(text, pos + this.ITALIC_START.length(),
                    endPos));

        return false;
    }
    
    
    protected boolean linkAtPos(String text, int pos) {
        // links look like: "description":http://url-of-something
        if (text.indexOf(this.LINK_START, pos) != pos)
            return false;

        // does not need to escape the LINK_START char, is it has no signifficant meaning
        // (unless it is folled by a LINK_START+LINK_MIDDLE)
        //if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
        //    return false;
        
        int middlePos = text.indexOf(this.LINK_MIDDLE, pos + this.LINK_START.length());
        middlePos = findTrueEndPos(text, middlePos, this.LINK_MIDDLE);

        if (middlePos > 0) {  
            
            //Check that the " at pos is the closest one to ":
            int testPos = text.indexOf(LINK_START, pos + LINK_START.length());
            testPos = findTrueEndPos(text, testPos, this.LINK_START);
            
            if (testPos != middlePos)
                return false;
            
            //Check that no LINE_SEPERATOR come before LINK_MIDDLE
            int lineSepPos =
                text.indexOf(LINE_SEPARATOR, pos + LINK_START.length());
            if ((lineSepPos > 0) && (lineSepPos < middlePos))
                return false;
            
            // Check that no PARAGRAPH_START or LIST_START come before
            // LINK_MIDDLE
            if (endOfBlockLevelElement(text, pos + this.LINK_START.length(), middlePos))
                return false;

            // Check that middlePos is not at the end of the text
            if (middlePos == text.length() - this.LINK_MIDDLE.length())
                return false;

            // Check that LINE_SEPARATOR or whitespace is not righT after
            // LINK_MIDDLE
            if ((text.charAt(middlePos + this.LINK_MIDDLE.length()) == ' ')
                    || (text.indexOf(this.LINE_SEPARATOR, pos + this.LINK_MIDDLE.length()) == pos
                            + this.LINK_MIDDLE.length()))
                return false;

            return true;
        }
        return false;
    }
    
    
    protected boolean refrenceAtPos(String text, int pos) {
        if (text.indexOf(this.REF_START, pos) != pos)
            return false;

        if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
            return false;
        
        int endPos = text.indexOf(this.REF_CLOSE, pos + this.REF_START.length());
        
        endPos = findTrueEndPos(text, endPos, this.REF_CLOSE);

        if (endPos > 0)
            return !(endOfBlockLevelElement(text, pos + this.REF_START.length(),
                    endPos));

        return false;
    }
    
    
    // check if certain special characters occur before the end pos of the
    // current element
    protected boolean endOfBlockLevelElement(String text, int pos, int endPos) {
        int paragraphPos = text.indexOf(this.PARAGRAPH_START, pos);
        if (paragraphPos > 0 && paragraphPos < endPos)
            return true;

        int listPos = text.indexOf(this.LIST_START, pos);
        if (listPos > 0 && listPos < endPos)
            return true;

        int numListPos = text.indexOf(this.NUMLIST_START, pos);
        if (numListPos > 0 && numListPos < endPos)
            return true;

        return false;
    }
    
    
    protected boolean subAtPos(String text, int pos) {
        // sub is given on the form: [sub:text]

        int startPos = text.indexOf(this.SUB_START, pos);

        if (startPos != pos) {
            return false;
        }
        
        if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
            return false;
        
        int endPos = text.indexOf(this.SUB_END, pos + this.SUB_START.length());
        endPos = findTrueEndPos(text, endPos, this.SUB_END);
        
        if (endPos < 0) {
            return false;
        }
        
        if (endPos > 0)
            return !endOfBlockLevelElement(text, pos + this.SUB_START.length(),
                    endPos);
        
        return true;
    }
    
    
    protected boolean superAtPos(String text, int pos) {
        // super is given on the form: [super:text]

        int startPos = text.indexOf(this.SUPER_START, pos);

        if (startPos != pos) {
            return false;
        }

        if (pos != 0 && this.ESCAPE == text.charAt(pos-1) )
            return false;
        
        int endPos = text.indexOf(this.SUPER_END, pos + this.SUPER_START.length());
        endPos = findTrueEndPos(text, endPos, this.SUPER_END);
        
        if (endPos < 0) {
            return false;
        }
        
        if (endPos > 0)
            return !endOfBlockLevelElement(text, pos + this.SUPER_START.length(),
                    endPos);
        
        return true;
    }

    
    /**
     * Helper method when parsing text paragraphs and checkin for formatted substrings
     * <br/>Returns index of unescaped ending character (i.e. true endPos)
     * <br/>Returns -1 if not found (i.e. this substring is actually escaped)
     * @param text
     * @param endPos
     * @param closingCharacter
     * @return index of end-character of formatted substring
     */
    private int findTrueEndPos(String text, int endPos, String closingCharacter) {
        while (endPos > 0 && text.charAt(endPos-1) == this.ESCAPE) {
                endPos = text.indexOf(closingCharacter, ++endPos);
        }
        return endPos;
    }

    
    
    
    public Element parseStructuredText(String text) {

        // Unifying new lines
        text = unifyNewlines(text);

        // Stripping all leading/triling new lines
        text = stripLeadingTrailingNewLines(text);        
        
        // Adding initial paragraph/list start new lines.
        text = this.PARAGRAPH_START + text;

        Element root = new Element(lookupTag("root"));
        
        int pos = 0;
        int nextPos = 0;
                
        while (pos < text.length()) {
            if (listAtPos(text, pos + this.LINE_SEPARATOR.length())) {
                nextPos = parseList(text, pos
                        + this.LINE_SEPARATOR.length(), root);
            } else if (numlistAtPos(text, pos
                    + this.LINE_SEPARATOR.length())) { // telle opp riktig
                nextPos = parseNumlist(text, pos
                        + this.LINE_SEPARATOR.length(), root);
            } else if (listAtPos(text, pos)) {
                nextPos = parseList(text, pos, root);
            } else if (numlistAtPos(text, pos)) {
                nextPos = parseNumlist(text, pos, root);
            } else if (paragraphAtPos(text, pos)) {
                nextPos = parseParagraph(text, pos, root);
            }

            pos = nextPos;
        }
        return root;
    }
    
    
    protected String stripLeadingTrailingNewLines(String text) {
        int startPos = 0;
        int endPos = text.length();

        while (startPos < endPos && text.charAt(startPos) == NEWLINE)
            startPos++;
        
        while (endPos > startPos && text.charAt(endPos - 1) == NEWLINE)
            endPos--;

        return text.substring(startPos, endPos);

    }

    protected String unifyNewlines( String text ) {
        text = text.replaceAll("\r\n", "\n");
        text = text.replaceAll("\r", "\n");
        return text;
    }
    
    protected int parseParagraph(String text, int startPos, Element root) {

        Element paragraphElement = new Element(lookupTag("paragraph"));
        root.addContent(paragraphElement);

        int pos = startPos + this.PARAGRAPH_START.length();

        /**
         * FIXME
         */
        
        // end paragraph when new paragraph or new list begins, or at end of text
        int endPos = text.length();
        int nextParagraph = text.indexOf(this.PARAGRAPH_START, pos);
        //nextParagraph = findTrueEndPos(text, nextParagraph, this.PARAGRAPH_START);
        int nextList = text.indexOf(this.LIST_START, pos);
        //nextList = findTrueEndPos(text, nextList, this.LIST_START);
        int nextNumlist = text.indexOf(this.NUMLIST_START, pos);
        //nextNumlist = findTrueEndPos(text, nextNumlist, this.NUMLIST_START);

        if (!(nextParagraph < 0) && (nextParagraph < endPos))
            endPos = nextParagraph;
        if (!(nextList < 0) && (nextList < endPos))
            endPos = nextList;
        if (!(nextNumlist < 0) && (nextNumlist < endPos))
            endPos = nextNumlist;

        while (pos < endPos) {
            // content will now be basic text
            pos = parseBasicText(text, pos, paragraphElement);
        }

        return endPos;
        // return without adding PARAGRAPH_CLOSE 
        // (because newline signifies a new element)
    }
    
        
    protected int parseEscape(String text, int startPos, Element root) {
        char escaped = text.charAt(startPos+1);
                
        // if escapable character
        if ( escaped == this.NEWLINE 
                || escaped == this.ESCAPE
                || String.valueOf(escaped).equals(this.LISTITEM_MARKER)
                || String.valueOf(escaped).equals(this.NUMLISTITEM_MARKER) ) {   
            // Add char at position AFTER the escape char
            root.addContent( String.valueOf(escaped) );
            return 2+startPos; // re-start parsing AFTER escaped character                                    
        }
        root.addContent( String.valueOf(this.ESCAPE) );
        return ++startPos;
    }
    
    
    protected int parseNewline(int startPos, Element root) {
        Element element = new Element(lookupTag("newline"));
        root.addContent(element);
        // Assuming newline is always represented by the "\n" character 
        // while parsing text in this class (length = 1)
        return ++startPos;
    }
    
    
    protected int parseList(String text, int startPos, Element root) {

        Element listElement = new Element(lookupTag("unordered-list"));
        root.addContent(listElement);

        int pos = startPos + this.LIST_START.length();
        
        /**
         * FIXME
         */

        // end paragraph when new paragraph or new list begins, or at end of text
        int endPos = text.length();
        int nextParagraph = text.indexOf(this.PARAGRAPH_START, pos);
        //nextParagraph = findTrueEndPos(text, nextParagraph, this.PARAGRAPH_START);
        int listClose = text.indexOf(this.LIST_CLOSE, pos);
        //listClose = findTrueEndPos(text, listClose, this.LIST_CLOSE);
        int numListStart = text.indexOf(this.NUMLIST_START, pos);
        //numListStart = findTrueEndPos(text, numListStart, this.NUMLIST_START);

        if (!(nextParagraph < 0) && (nextParagraph < endPos))
            endPos = nextParagraph;
        if (!(listClose < 0) && (listClose < endPos))
            endPos = listClose;
        if (!(numListStart < 0) && (numListStart < endPos))
            endPos = numListStart;

        pos = pos - this.LISTITEM_START.length();
        // Move pos back to listitem start...
        while (pos < endPos) {
            // only listitems are defined
            pos = parseListitem(text, pos, listElement);
        }
        return endPos;
        // return without adding PARAGRAPH_CLOSE 
        // (because newline signifies a new element)
    }
    
    
    protected int parseNumlist(String text, int startPos, Element root) {

        Element listElement = new Element(lookupTag("ordered-list"));
        root.addContent(listElement);

        int pos = startPos + this.NUMLIST_START.length();
        
        /**
         * FIXME
         */
        
        // end paragraph when new paragraph or new list begins, or at end of text
        int endPos = text.length();
        int nextParagraph = text.indexOf(this.PARAGRAPH_START, pos);
        //nextParagraph = findTrueEndPos(text, nextParagraph, this.PARAGRAPH_START);
        int numlistClose = text.indexOf(this.NUMLIST_CLOSE, pos);
        //numlistClose = findTrueEndPos(text, numlistClose, this.NUMLIST_CLOSE);
        int listStart = text.indexOf(this.LIST_START, pos);
        //listStart = findTrueEndPos(text, listStart, this.LIST_START);
        
        if (!(nextParagraph < 0) && (nextParagraph < endPos))
            endPos = nextParagraph;
        if (!(numlistClose < 0) && (numlistClose < endPos))
            endPos = numlistClose;
        if (!(listStart < 0) && (listStart < endPos))
            endPos = listStart;

        pos = pos - this.NUMLISTITEM_START.length();
        // Move pos back to listitem start...
        while (pos < endPos) {
            // only listitems are defined
            pos = parseNumlistitem(text, pos, listElement);
        }
        return endPos;
        // return without adding PARAGRAPH_CLOSE 
        // (because newline signifies a new element)
    }
    
    
    protected int parseListitem(String text, int startPos, Element parent) {
        int pos = startPos + this.LISTITEM_START.length();

        int listitemClosePos = text.indexOf(this.LISTITEM_START, pos);
        // Close when new listitem starts
        int listClosePos = text.indexOf(this.LIST_CLOSE, pos);
        int numlistStartPos = text.indexOf(this.NUMLISTITEM_START, pos);

        int endPos = text.length();
        if (!(listitemClosePos < 0))
            endPos = listitemClosePos;
        if (!(listClosePos < 0) && (listClosePos < endPos))
            endPos = listClosePos;
        if (!(numlistStartPos < 0) && (numlistStartPos < endPos))
            endPos = numlistStartPos;

        Element listitemElement = new Element(lookupTag("listitem"));
        parent.addContent(listitemElement);

        while (pos < endPos) {
            // inneholdet skal n� v�re basictekst
            pos = parseBasicText(text, pos, listitemElement);
        }

        return endPos;
    }
    
    
    protected int parseNumlistitem(String text, int startPos, Element parent) {
        int pos = startPos + this.NUMLISTITEM_START.length();

        int numlistitemClosePos = text.indexOf(this.NUMLISTITEM_START, pos);
        // Close when new listitem starts
        int numlistClosePos = text.indexOf(this.NUMLIST_CLOSE, pos);
        int listStartPos = text.indexOf(this.LISTITEM_START, pos);

        int endPos = text.length();
        if (!(numlistitemClosePos < 0))
            endPos = numlistitemClosePos;
        if (!(numlistClosePos < 0) && (numlistClosePos < endPos))
            endPos = numlistClosePos;
        if (!(listStartPos < 0) && (listStartPos < endPos))
            endPos = listStartPos;

        Element listitemElement = new Element(lookupTag("listitem"));
        parent.addContent(listitemElement);

        while (pos < endPos) {
            // content will now be basictekst
            pos = parseBasicText(text, pos, listitemElement);
        }

        return endPos;
    }
    
    
    // basicText consist of bold, italic, link, refrence, subscript and
    // superscript.
    protected int parseBasicText(String basicText, int pos, Element parent) {

        int nextPos = pos;
        
        if (boldAtPos(basicText, pos)) {
            nextPos = parseBold(basicText, pos, parent);
        } else if (italicAtPos(basicText, pos)) {
            nextPos = parseItalic(basicText, pos, parent);
        } else if (linkAtPos(basicText, pos)) {
            nextPos = parseLink(basicText, pos, parent);
        } else if (subAtPos(basicText, pos)) {
            nextPos = parseSub(basicText, pos, parent);
        } else if (superAtPos(basicText, pos)) {
            nextPos = parseSuper(basicText, pos, parent);
        // 'reference' MUST be parsed after 'sub' and 'super'
        // as the syntax used is a special cases of reference
        } else if (refrenceAtPos(basicText, pos)) {
            nextPos = parseRefrence(basicText, pos, parent);
        } else if (newlineAtPos(basicText, pos)) {
            nextPos = parseNewline(pos, parent);
        } else {
            nextPos = parsePlainText(basicText, pos, parent);
        }

        return nextPos;
    }
    
    /*
    // helper method to remove ESCAPE characters from an substring
    // (when using escape character for signifficant symbols within a context)
    private StringBuffer removeEscapeChars(String text, int startPos, int endPos, char code) {
        StringBuffer substring = new StringBuffer( text.substring(startPos, endPos) );
        for (int i = 0; i < substring.length(); i++) {
            if (substring.charAt(i) == this.ESCAPE && substring.charAt(i+1) == code) {
                substring.deleteCharAt(i);
            }
        }
        return substring;
    }
    
    private StringBuffer removeEscapeChars(String text, int startPos, int endPos) {
        StringBuffer substring = new StringBuffer( text.substring(startPos, endPos) );
        for (int i = 0; i < substring.length(); i++) {
            if (substring.charAt(i) == this.ESCAPE) {
                substring.deleteCharAt(i);
            }
        }
        return substring;
    }
    */
    
        
    protected int parseBold(String text, int pos, Element element) {
        int startPos = pos + this.BOLD_START.length();
        int endPos = text.indexOf(BOLD_CLOSE, startPos);
        
        endPos = findTrueEndPos(text, endPos, this.BOLD_CLOSE);
        
        String boldText = text.substring(startPos, endPos);
        // Remove escape character (to save without ESCAPE in xml)
        boldText = removeEscapeBold(boldText);
        
        Element bold = new Element(lookupTag("bold"));
        bold.addContent(boldText);
        element.addContent(bold);

        return endPos + this.BOLD_CLOSE.length();
    }
    
    
    protected int parseItalic(String text, int pos, Element element) {
        int startPos = pos + this.ITALIC_START.length();
        int endPos = text.indexOf(ITALIC_CLOSE, startPos);
        
        endPos = findTrueEndPos(text, endPos, this.ITALIC_CLOSE);
        
        String italicText = text.substring(startPos, endPos);
        // Remove escape character (to save without ESCAPE in xml)
        italicText = removeEscapeItalic(italicText);
        
        Element italic = new Element(lookupTag("italic"));
        italic.addContent(italicText);
        element.addContent(italic);

        return endPos + this.ITALIC_CLOSE.length();
    }
    
    
    protected int parseRefrence(String text, int pos, Element element) {
        int startPos = pos + this.REF_START.length();
        int endPos = text.indexOf(this.REF_CLOSE, startPos);

        endPos = findTrueEndPos(text, endPos, this.REF_CLOSE);
        
        String referenceText = text.substring(startPos, endPos);
        Element refrence = new Element(lookupTag("reference"));

        int attributePos = referenceText.indexOf(this.REF_ATTRIBUTE);
        attributePos = findTrueEndPos(referenceText, attributePos, this.REF_ATTRIBUTE);
        
        if (attributePos > -1) {
            String typeAttributeValue = referenceText.substring(0, attributePos);
            typeAttributeValue = removeEscapeReferenceMiddle(typeAttributeValue);
            typeAttributeValue = removeEscapeRightBracket(typeAttributeValue);
            
            String elementValue = referenceText.substring(attributePos 
                    + this.REF_ATTRIBUTE.length(), referenceText.length());
            elementValue = removeEscapeRightBracket(elementValue);

            Attribute typeAttribute = new Attribute(
                    lookupTag("reference-type"), typeAttributeValue);
            refrence.setAttribute(typeAttribute);
            refrence.addContent(elementValue);

        } else {
            referenceText = removeEscapeReferenceMiddle(referenceText);
            referenceText = removeEscapeRightBracket(referenceText);
            refrence.addContent(referenceText);
        }

        element.addContent(refrence);

        return endPos + this.REF_CLOSE.length();
    }
    
    
    // The only escapeable sequence for URLs will be the '":' within a description! 
    protected int parseLink(String text, int pos, Element parent) {
        
        int middlePos = text.indexOf(this.LINK_MIDDLE, pos);
        middlePos = findTrueEndPos(text, middlePos, this.LINK_MIDDLE);
        
        String description = text.substring(pos + this.LINK_START.length(), middlePos);
        description = removeEscapeQuotes(description); 
        
        int endUrl = pos + description.length() + this.LINK_MIDDLE.length();
        while (endUrl < text.length() 
                && text.indexOf(" ", endUrl) != endUrl
                && text.indexOf(this.LINE_SEPARATOR, endUrl) != endUrl
                && text.indexOf(". ", endUrl) != endUrl
                && text.indexOf(", ", endUrl) != endUrl
                && text.indexOf("; ", endUrl) != endUrl) {
            endUrl++;
        }
        
        String url = text.substring(middlePos + this.LINK_MIDDLE.length(), endUrl);

        Element link = new Element(lookupTag("link"));
        Element e = new Element(lookupTag("url-description"));
                
        e.addContent(description);        
        link.addContent(e);

        e = new Element(lookupTag("url"));
        e.addContent(url);
        link.addContent(e);

        parent.addContent(link);

        return endUrl;
    }
    
    
    protected int parseSub(String text, int pos, Element parent) {
        int startPos = pos + this.SUB_START.length();
        int endPos = text.indexOf(this.SUB_END, startPos);
        
        endPos = findTrueEndPos(text, endPos, this.SUB_END);
        
        String subText = text.substring(startPos, endPos);
        // Remove escape character (to save without ESCAPE in xml)
        subText = removeEscapeRightBracket(subText);
        
        Element sub = new Element(lookupTag("sub"));
        sub.addContent(subText);
        parent.addContent(sub);

        return endPos + this.SUB_END.length();
    }
    
      
    protected int parseSuper(String text, int pos, Element parent) {
        int startPos = pos + this.SUPER_START.length();
        int endPos = text.indexOf(this.SUPER_END, startPos);
        
        endPos = findTrueEndPos(text, endPos, this.SUPER_END);
        
        String superText = text.substring(startPos, endPos);
        // Remove escape character (to save without ESCAPE in xml)
        superText = removeEscapeRightBracket(superText);
        
        Element sup = new Element(lookupTag("sup"));
        sup.addContent(superText);
        parent.addContent(sup);

        return endPos + this.SUPER_END.length();
    }
    

    protected int parsePlainText(String text, int pos, Element parent) {
        int startPos = pos;

        // parse single char until one with a special meaning occurs
        while (pos < text.length()) {
            pos++; // first position is already examined
                        
            if ( paragraphAtPos(text, pos) 
                    || listAtPos(text, pos)
                    || boldAtPos(text, pos) 
                    || refrenceAtPos(text, pos)
                    || italicAtPos(text, pos) 
                    || listitemAtPos(text, pos)
                    || numlistitemAtPos(text, pos) 
                    || linkAtPos(text, pos)
                    || subAtPos(text, pos) 
                    || superAtPos(text, pos)
                    || newlineAtPos(text, pos) )
                break;
        }
                
        String content = text.substring(startPos, pos);
        
        // remove ESCAPE char where needed
        content = removeEscapeBold(content); 
        content = removeEscapeItalic(content); 
        content = removeEscapeNewline(content); 
        //content = removeEscapeQuotes(content); // for link // No need to do in general text 
        content = removeEscapeLeftBracket(content); // for SUB, SUP and REFERENCE 
        content = removeEscapeListItem(content); 
        content = removeEscapeNumListItem(content);
        
        parent.addContent(content);

        return pos;
    }

    
    /**
     * Generate structured text from an XML structure.
     * 
     * @param element
     *            the root <code>Element</code> of the XML structure
     * @return a <code>String</code> containing the structured text
     */
    protected String generateStructuredText(Element element) {

        StringBuffer buffer = new StringBuffer();
        
        for (Object object: element.getContent()) {

            if (object instanceof Element) {
                Element child = (Element) object;
                                
                String tagName = reverseLookupTag(child.getName());
                
                if (tagName.equals("bold")) {
                    buffer.append(this.BOLD_START);
                    //buffer.append(generateStructuredText(child));
                    buffer.append( addEscapeBold(child.getText()) );
                    buffer.append(this.BOLD_CLOSE);
                } else if (tagName.equals("italic")) {
                    buffer.append(this.ITALIC_START); 
                    //buffer.append(generateStructuredText(child));
                    buffer.append( addEscapeItalic(child.getText()) );
                    buffer.append(this.ITALIC_CLOSE);
                } else if (tagName.equals("reference")) {
                    // ingen ESCAPE-karakter kan brukes i referanse
                    buffer.append(this.REF_START);
                    if (child.getAttributeValue(lookupTag("reference-type")) != null) {
                        String attributeValue = child.getAttributeValue(lookupTag("reference-type"));
                        attributeValue = addEscapeReferenceMiddle(attributeValue);
                        attributeValue = addEscapeRightBracket(attributeValue);
                        buffer.append( attributeValue );
                        buffer.append(this.REF_ATTRIBUTE);
                    }
                    String elementValue = child.getText();
                    elementValue = addEscapeReferenceMiddle(elementValue);
                    elementValue = addEscapeRightBracket(elementValue);
                    
                    buffer.append( elementValue );
                    buffer.append(this.REF_CLOSE);
                } else if (tagName.equals("link")) {
                    // buffer.append();
                    buffer.append(generateStructuredText(child));
                    // buffer.append();
                } else if (tagName.equals("url-description")) {
                    buffer.append(this.LINK_START);
                    buffer.append(addEscapeQuotes(child.getText()));
                    buffer.append(this.LINK_MIDDLE);
                } else if (tagName.equals("url")) {
                    buffer.append(child.getText());
                    // buffer.append();
                } else if (tagName.equals("unordered-list")
                        || tagName.equals("ordered-list")) {
                    buffer.append(this.LINE_SEPARATOR);
                    buffer.append(generateStructuredText(child));
                    // buffer.append(LIST_CLOSE);
                } else if (tagName.equals("listitem")) {
                    String parentName = reverseLookupTag(element.getName());
                    if (parentName.equals("ordered-list"))
                        buffer.append(this.NUMLISTITEM_START);
                    else
                        buffer.append(this.LISTITEM_START);
                    buffer.append(generateStructuredText(child));
                    // buffer.append();
                } else if (tagName.equals("paragraph")) {
                    buffer.append(this.PARAGRAPH_START);
                    buffer.append(generateStructuredText(child));
                    // buffer.append();
                } else if (tagName.equals("newline")) {
                    buffer.append(this.NEWLINE);
                } else if (tagName.equals("sub")) {
                    buffer.append(this.SUB_START);
                    //buffer.append(generateStructuredText(child));
                    buffer.append(addEscapeRightBracket(child.getText()));
                    buffer.append(this.SUB_END);
                } else if (tagName.equals("sup")) {
                    buffer.append(this.SUPER_START);
                    //buffer.append(generateStructuredText(child));
                    buffer.append(addEscapeRightBracket(child.getText()));
                    buffer.append(this.SUPER_END);
                } else { // plaintextelement
                    throw new StructuredTextException(
                            "Unexpected element name: " + tagName);
                }
            } else if (object instanceof Text) { // plaintextelement

                Element parent = (Element) ((Text) object).getParent();
                String tagName = reverseLookupTag(parent.getName());
                                
                // Don't include text nodes for the following parent
                // ("block level") elements:
                if (!"root".equals(tagName)
                        && !"unordered-list".equals(tagName)
                        && !"ordered-list".equals(tagName)
                        && !"link".equals(tagName)) {
                    Text child = (Text) object;
                    
                    String content = child.getText();
                    
                    // add ESCAPE char where needed
                    content = addEscapeBold(content); 
                    content = addEscapeItalic(content); 
                    content = addEscapeNewline(content); 
                    //content = addEscapeQuotes(content); // for link // No need to do in general text
                    content = addEscapeLeftBracket(content); // for SUB, SUP and REFERENCE 
                    content = addEscapeListItem(content); 
                    content = addEscapeNumListItem(content);
                                                            
                    buffer.append( content );
                }
            }
        } // end for-loop

        return buffer.toString();
    }
    
        
    // BOLD_START replaced by ESCAPE+BOLD_START
    private String addEscapeBold(String content) {
        return content.replaceAll("\\*", "\\\\*"); 
        // NB! 4x '\' because replaceAll() does regexp-type replacent of literals here as well 
    }
    // ESCAPE+BOLD_START replaced by BOLD_START 
    private String removeEscapeBold(String content) {
        return content.replaceAll("\\\\\\*", "\\*");
    }
    
    // ITALIC_START replaced by ESCAPE+ITALIC_START
    private String addEscapeItalic(String content) {
        return content.replaceAll("\\_", "\\\\_"); 
    }
    // ESCAPE+ITALIC_START replaced by ITALIC_START 
    private String removeEscapeItalic(String content) {
        return content.replaceAll("\\\\\\_", "\\_");
    }
    
    // REFERENCE+SUB+SUPER_START replaced by ESCAPE+REFERENCE+SUB+SUPER_START    
    private String addEscapeLeftBracket(String content) {
        return content.replaceAll("\\[", "\\\\[");
    }
    // ESCAPE+REFERENCE+SUB+SUPER_START replaced by REFERENCE+SUB+SUPER_START
    private String removeEscapeLeftBracket(String content) {
        return content.replaceAll("\\\\\\[", "\\[");
    }
    // REFERENCE+SUB+SUPER_END replaced by ESCAPE+REFERENCE+SUB+SUPER_END
    private String addEscapeRightBracket(String content) {
        return content.replaceAll("\\]", "\\\\]");
    }
    // ESCAPE+REFERENCE+SUB+SUPER_END replaced by REFERENCE+SUB+SUPER_END
    private String removeEscapeRightBracket(String content) {
        return content.replaceAll("\\\\\\]", "\\]");
    }
    
// escaping of newlines is not implementet at the moment
    // NEWLINE replaced by ESCAPE+NEWLINE
    private String addEscapeNewline(String content) {
        /**
         * FIXME
         */
        return content;
        //return content.replaceAll("\n", "\\\\\n");
    }
    // ESCAPE+NEWLINE replaced by NEWLINE
    private String removeEscapeNewline(String content) {
        /**
         * FIXME
         */
        return content;
//        StringBuffer contentBuffer = new StringBuffer( content );
//        System.out.println("stringbuffer: '" + contentBuffer + "'");
//        
//        for (int i = 0; i < contentBuffer.length(); i++) {
//            if (contentBuffer.charAt(i) == this.ESCAPE 
//                    && i < content.length()-1 
//                    && contentBuffer.charAt(i+1) == '\n') {
//                contentBuffer.deleteCharAt(i);
//            }
//        }
//        return contentBuffer.toString();
    }
    
    // LIST_ITEM_START replaced by ESCAPE+LIST_ITEM
    private String addEscapeListItem(String content) {
        return content.replaceAll("^- ", "\\\\- ");
    }
    // ESCAPE+LIST_ITEM_START replaced by LIST_ITEM
    private String removeEscapeListItem(String content) {
        return content.replaceAll("^\\\\- ", "- ");
    }
    
    // NUMLIST_ITEM_START replaced by ESCAPE+NUMLIST_ITEM_START
    private String addEscapeNumListItem(String content) {
        return content.replaceAll("^# ", "\\\\# ");
    }
    // ESCAPE+NUMLIST_ITEM_START replaced by NUMLIST_ITEM_START
    private String removeEscapeNumListItem(String content) {
        return content.replaceAll("^\\\\# ", "# ");
    }    
    
    // REFERENCE_MIDDLE replaced by ESCAPE+REFERENCE_MIDDLE
    private String addEscapeReferenceMiddle(String content) {
        return content.replaceAll(":", "\\\\:");
    }
    // ESCAPE+REFERENCE_MIDDLE replaced by REFERENCE_MIDDLE
    private String removeEscapeReferenceMiddle(String content) {
        return content.replaceAll("\\\\:", ":");
    }
    
//  Not in use anymore!
    // LINK_MIDDLE replaced by ESCAPE+LINK_MIDDLE
//    private String addEscapeLinkMiddle(String content) {
//        return content.replaceAll("\":", "\\\\\":");
//    }
    // ESCAPE+LINK_MIDDLE replaced by LINK_MIDDLE
//    private String removeEscapeLinkMiddle(String content) {
//        return content.replaceAll("\\\\\"\\:", "\":");
//    }
    
    // LINK_START replaced by ESCAPE+LINK_START
    private String addEscapeQuotes(String content) {
        return content.replaceAll("\"", "\\\\\"");
    }
    // ESCAPE+LINK_START replaced by LINK_START
    private String removeEscapeQuotes(String content) {
        return content.replaceAll("\\\\\"", "\"");
    }

    
    public String parseElement(Element root) {
        if (root.getChildren().size() == 0)
            return "";

        String text = generateStructuredText(root);

        // Remove possible inconsistencies caused by directly edited xml
        return stripLeadingTrailingNewLines(text);

//        Element child = (Element) root.getChildren().get(0);
//
//        // Delete first paragraph/list start
//
//        if ("paragraph".equals(reverseLookupTag(child.getName()))) {
//            return text.substring(this.PARAGRAPH_START.length());
//        }
//
//        if ("unordered-list".equals(reverseLookupTag(child.getName()))) {
//            return text.substring((this.LINE_SEPARATOR + this.LINE_SEPARATOR).length());
//        }
//
//        if ("ordered-list".equals(reverseLookupTag(child.getName()))) {
//            return text.substring((this.LINE_SEPARATOR + this.LINE_SEPARATOR).length());
//        }
//        return text;
    }
    
    
    // Various helper methods
    private String lookupTag(String tagName) {
        String tag = this.tagNames.get(tagName);
        if (tag == null)
            tag = tagName;
        return tag;
    }
    
    
    private String reverseLookupTag(String mappedTag) {
        for (String tagName: this.tagNames.keySet()) {
            if ((this.tagNames.get(tagName)).equals(mappedTag)) {
                return tagName;
            }
        }
        return mappedTag;
    }
    
    
    public static void dumpXML(Document doc, PrintStream out) {
        try {
            Format format = Format.getPrettyFormat();
            
            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.setFormat(format);
            // XMLOutputter xmlOutputter = new XMLOutputter(" ", true);
            //
            // xmlOutputter.setExpandEmptyElements(true);
            // xmlOutputter.setTextNormalize(false);
            String xml = xmlOutputter.outputString(doc);
            out.println(xml);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
    
    // brukes for å skrive xml'en ut til strucured text
    public static void dumpXML(Element element, PrintStream out) {
        try {
            Format format = Format.getPrettyFormat();
            
            XMLOutputter xmlOutputter = new XMLOutputter();
            xmlOutputter.setFormat(format);
            // //XMLOutputter takes a JDOM tree and formats it to a stream as
            // XML.
            // XMLOutputter xmlOutputter = new XMLOutputter(" ", true);
            // xmlOutputter.setTextNormalize(true);
            String xml = xmlOutputter.outputString(element);
            out.println(xml);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
    
        
    
    
    private static Map<String, String> getEnglishTagNames() {
        Map<String, String> tagNames = new HashMap<String, String>();
        tagNames.put("root", "evenstructuredtext");
        tagNames.put("bold", "bold");
        tagNames.put("italic", "it");
        tagNames.put("link", "link");
        tagNames.put("url", "url");
        tagNames.put("url-description", "description");
        tagNames.put("unordered-list", "unsortedlist");
        tagNames.put("ordered-list", "sortedlist");
        tagNames.put("listitem", "listitem");
        tagNames.put("paragraph", "paragraph");
        tagNames.put("reference", "refrence");
        tagNames.put("reference-type", "type");
        tagNames.put("sub", "sub");
        tagNames.put("sup", "sup");
        tagNames.put("newline", "newline");
        return tagNames;
    }
    
    private static Map<String, String> getNorwegianTagNames() {
        Map<String, String> tagNames = new HashMap<String, String>();
        tagNames.put("root", "fritekst");
        tagNames.put("bold", "fet");
        tagNames.put("italic", "kursiv");
        tagNames.put("reference", "referanse");
        tagNames.put("reference-type", "type");
        tagNames.put("reference-language", "spraak");
        tagNames.put("link", "weblenke");
        tagNames.put("url", "webadresse");
        tagNames.put("url-description", "lenketekst");
        tagNames.put("unordered-list", "punktliste");
        tagNames.put("ordered-list", "nummerertliste");
        tagNames.put("listitem", "listepunkt");
        tagNames.put("paragraph", "avsnitt");
        tagNames.put("sub", "sub");
        tagNames.put("sup", "sup");
        tagNames.put("newline", "linjeskift");
        return tagNames;
    }
}
