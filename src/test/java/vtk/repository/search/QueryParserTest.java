/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.repository.search;

import java.util.List;

import vtk.repository.Namespace;
import vtk.repository.search.query.AclExistsQuery;
import vtk.repository.search.query.AclReadForAllQuery;
import vtk.repository.search.query.AndQuery;
import vtk.repository.search.query.NameTermQuery;
import vtk.repository.search.query.OrQuery;
import vtk.repository.search.query.PropertyExistsQuery;
import vtk.repository.search.query.PropertyPrefixQuery;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.PropertyWildcardQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.repository.search.query.TypeTermQuery;
import vtk.repository.search.query.UriDepthQuery;
import vtk.repository.search.query.UriPrefixQuery;
import vtk.repository.search.query.UriTermQuery;
import vtk.testing.mocktypes.MockResourceTypeTree;

import static org.junit.Assert.*;
import org.junit.Test;
import vtk.repository.search.query.NamePrefixQuery;
import vtk.repository.search.query.NameWildcardQuery;

public class QueryParserTest {

    private final QueryParser queryParser;

    public QueryParserTest() {
        this.queryParser = new QueryParserImpl(new MockResourceTypeTree());
    }

    @Test
    public void simplePropertyQuery() {
        Query q = queryParser.parse("a=b");

        assertTrue(q instanceof PropertyTermQuery);

        PropertyTermQuery ptq = (PropertyTermQuery) q;

        assertEquals(Namespace.DEFAULT_NAMESPACE, ptq.namespace());
        assertEquals("a", ptq.name());
        assertEquals("b", ptq.getTerm());
    }

    @Test
    public void propertyQueryWithComplexValueAttributeSpecifier() {
        Query q = queryParser.parse("a@foo.bar = v@lue");

        assertTrue(q instanceof PropertyTermQuery);
        PropertyTermQuery ptq = (PropertyTermQuery) q;
        assertEquals(Namespace.DEFAULT_NAMESPACE, ptq.namespace());
        assertEquals("a", ptq.name());
        assertEquals("foo.bar", ptq.complexValueAttributeSpecifier().get());
        assertEquals("v@lue", ptq.getTerm());

        q = queryParser.parse("prefix:propname@jsonblob.field =:v@lue:");

        assertTrue(q instanceof PropertyTermQuery);
        ptq = (PropertyTermQuery) q;
        assertEquals("prefix", ptq.namespace().getPrefix());
        assertEquals("propname", ptq.name());
        assertEquals("jsonblob.field", ptq.complexValueAttributeSpecifier().get());
        assertEquals(":v@lue:", ptq.getTerm());

        q = queryParser.parse("prefix:propname@foo=*bar*");
        assertTrue(q instanceof PropertyWildcardQuery);
        PropertyWildcardQuery pwq = (PropertyWildcardQuery) q;
        assertEquals("prefix", pwq.namespace().getPrefix());
        assertEquals("propname", pwq.name());
        assertEquals("foo", pwq.complexValueAttributeSpecifier().get());
        assertEquals("*bar*", pwq.getTerm());

        q = queryParser.parse("prefix:propname@foo=pre*");
        assertTrue(q instanceof PropertyPrefixQuery);
        PropertyPrefixQuery ppq = (PropertyPrefixQuery) q;
        assertEquals("prefix", ppq.namespace().getPrefix());
        assertEquals("propname", ppq.name());
        assertEquals("foo", ppq.complexValueAttributeSpecifier().get());
        assertEquals("pre", ppq.getTerm());

        q = queryParser.parse("prefix:propname@foo exists");
        assertTrue(q instanceof PropertyExistsQuery);
        PropertyExistsQuery peq = (PropertyExistsQuery) q;
        assertEquals("prefix", peq.namespace().getPrefix());
        assertEquals("propname", peq.name());
        assertEquals("foo", peq.complexValueAttributeSpecifier().get());
        assertFalse(peq.isInverted());

        q = queryParser.parse("prefix:propname@foo   not exists");
        assertTrue(q instanceof PropertyExistsQuery);
        peq = (PropertyExistsQuery) q;
        assertEquals("prefix", peq.namespace().getPrefix());
        assertEquals("propname", peq.name());
        assertEquals("foo", peq.complexValueAttributeSpecifier().get());
        assertTrue(peq.isInverted());

        q = queryParser.parse("propname@foo   !exists");
        assertTrue(q instanceof PropertyExistsQuery);
        peq = (PropertyExistsQuery) q;
        assertNull(peq.namespace().getPrefix());
        assertEquals("propname", peq.name());
        assertEquals("foo", peq.complexValueAttributeSpecifier().get());
        assertTrue(peq.isInverted());

        q = queryParser.parse("propname@.!exists");
        assertTrue(q instanceof PropertyExistsQuery);
        peq = (PropertyExistsQuery) q;
        assertNull(peq.namespace().getPrefix());
        assertEquals("propname", peq.name());
        assertEquals(".", peq.complexValueAttributeSpecifier().get());
        assertTrue(peq.isInverted());
    }

    @Test
    public void existsQuery() {
        Query q = queryParser.parse("p:r exists");

        assertTrue(q instanceof PropertyExistsQuery);

        PropertyExistsQuery peq = (PropertyExistsQuery) q;
        assertEquals("r", peq.name());
        assertEquals("p", peq.namespace().getPrefix());
        assertFalse(peq.isInverted());


        q = queryParser.parse("p:r not exists");

        assertTrue(q instanceof PropertyExistsQuery);

        peq = (PropertyExistsQuery) q;
        assertEquals("r", peq.name());
        assertEquals("p", peq.namespace().getPrefix());
        assertTrue(peq.isInverted());


        q = queryParser.parse("p:r EXISTS");

        assertTrue(q instanceof PropertyExistsQuery);

        peq = (PropertyExistsQuery) q;
        assertEquals("r", peq.name());
        assertEquals("p", peq.namespace().getPrefix());
        assertFalse(peq.isInverted());
        

        q = queryParser.parse("p:r NOT EXISTS");

        assertTrue(q instanceof PropertyExistsQuery);

        peq = (PropertyExistsQuery) q;
        assertEquals("r", peq.name());
        assertEquals("p", peq.namespace().getPrefix());
        assertTrue(peq.isInverted());

        q = queryParser.parse("r !EXISTS");

        assertTrue(q instanceof PropertyExistsQuery);

        peq = (PropertyExistsQuery) q;
        assertEquals("r", peq.name());
        assertEquals(Namespace.DEFAULT_NAMESPACE, peq.namespace());
        assertTrue(peq.isInverted());
    }
    
    @Test
    public void aclQuery() {
        Query q = queryParser.parse("acl EXISTS");
        assertTrue(q instanceof AclExistsQuery);
        assertFalse(((AclExistsQuery)q).isInverted());

        q = queryParser.parse("acl !EXISTS");
        assertTrue(q instanceof AclExistsQuery);
        assertTrue(((AclExistsQuery)q).isInverted());

        q = queryParser.parse("acl NOT EXISTS");
        assertTrue(q instanceof AclExistsQuery);
        assertTrue(((AclExistsQuery)q).isInverted());
        
        q = queryParser.parse("acl ALL");
        assertTrue(q instanceof AclReadForAllQuery);
        assertFalse(((AclReadForAllQuery)q).isInverted());

        q = queryParser.parse("acl !ALL");
        assertTrue(q instanceof AclReadForAllQuery);
        assertTrue(((AclReadForAllQuery)q).isInverted());

        q = queryParser.parse("acl NOT ALL");
        assertTrue(q instanceof AclReadForAllQuery);
        assertTrue(((AclReadForAllQuery)q).isInverted());
    }

    @Test
    public void escaping() {
        Query q = queryParser.parse("uri = /i\\ am\\ a\\ file\\ with\\ spaces\\(YES\\)\\?\\*\\>\\<\\=\\x\\\\\\/\\ AND\\ uri\\=/hoho.txt");

        assertTrue(q instanceof UriTermQuery);
        assertEquals("/i am a file with spaces(YES)?*><=x\\\\/ AND uri=/hoho.txt", ((UriTermQuery) q).getUri());
        assertEquals(((UriTermQuery) q).getOperator(), TermOperator.EQ);
    }

    @Test
    public void namePrefixQuery() {
        Query q = queryParser.parse("name = foo*");
        assertTrue(q instanceof NamePrefixQuery);
        NamePrefixQuery npq = (NamePrefixQuery)q;
        assertEquals(TermOperator.EQ, npq.getOperator());
        assertEquals("foo", npq.getTerm());

        q = queryParser.parse("name != foo*");
        assertTrue(q instanceof NamePrefixQuery);
        npq = (NamePrefixQuery)q;
        assertEquals(TermOperator.NE, npq.getOperator());
        assertEquals("foo", npq.getTerm());

        q = queryParser.parse("name =~ foo*");
        assertTrue(q instanceof NamePrefixQuery);
        npq = (NamePrefixQuery)q;
        assertEquals(TermOperator.EQ_IGNORECASE, npq.getOperator());
        assertEquals("foo", npq.getTerm());

        q = queryParser.parse("name !=~ foo*");
        assertTrue(q instanceof NamePrefixQuery);
        npq = (NamePrefixQuery)q;
        assertEquals(TermOperator.NE_IGNORECASE, npq.getOperator());
        assertEquals("foo", npq.getTerm());
    }

    @Test
    public void nameWildcardQuery() {
        Query q = queryParser.parse("name = *foo");
        assertTrue(q instanceof NameWildcardQuery);

        q = queryParser.parse("name = f*oo");
        assertTrue(q instanceof NameWildcardQuery);

        q = queryParser.parse("name = f*oo*");
        assertTrue(q instanceof NameWildcardQuery);

        q = queryParser.parse("name = *foo*");
        assertTrue(q instanceof NameWildcardQuery);
        NameWildcardQuery npq = (NameWildcardQuery)q;
        assertEquals(TermOperator.EQ, npq.getOperator());
        assertEquals("*foo*", npq.getTerm());

        q = queryParser.parse("name != *foo*");
        assertTrue(q instanceof NameWildcardQuery);
        npq = (NameWildcardQuery)q;
        assertEquals(TermOperator.NE, npq.getOperator());
        assertEquals("*foo*", npq.getTerm());

        q = queryParser.parse("name =~ *foo*");
        assertTrue(q instanceof NameWildcardQuery);
        npq = (NameWildcardQuery)q;
        assertEquals(TermOperator.EQ_IGNORECASE, npq.getOperator());
        assertEquals("*foo*", npq.getTerm());

        q = queryParser.parse("name !=~ *foo*");
        assertTrue(q instanceof NameWildcardQuery);
        npq = (NameWildcardQuery)q;
        assertEquals(TermOperator.NE_IGNORECASE, npq.getOperator());
        assertEquals("*foo*", npq.getTerm());
    }
    
    @Test
    public void uriPrefixQuery() {
        Query q = queryParser.parse("uri = /foo*");
        assertTrue(q instanceof UriPrefixQuery);
        UriPrefixQuery upq = (UriPrefixQuery)q;
        assertTrue(upq.isIncludeSelf());
        assertFalse(upq.isInverted());
        assertEquals("/foo", upq.getUri());
        
        q = queryParser.parse("uri = /foo/*");
        assertTrue(q instanceof UriPrefixQuery);
        upq = (UriPrefixQuery)q;
        assertFalse(upq.isIncludeSelf());
        assertFalse(upq.isInverted());
        assertEquals("/foo", upq.getUri());
        
        q = queryParser.parse("uri != /foo*");
        assertTrue(q instanceof UriPrefixQuery);
        upq = (UriPrefixQuery)q;
        assertTrue(upq.isIncludeSelf());
        assertTrue(upq.isInverted());
        assertEquals("/foo", upq.getUri());

        q = queryParser.parse("uri != /foo/*");
        assertTrue(q instanceof UriPrefixQuery);
        upq = (UriPrefixQuery)q;
        assertFalse(upq.isIncludeSelf());
        assertTrue(upq.isInverted());
        assertEquals("/foo", upq.getUri());

        q = queryParser.parse("uri = /*");
        assertTrue(q instanceof UriPrefixQuery);
        upq = (UriPrefixQuery)q;
        assertFalse(upq.isIncludeSelf());
        assertFalse(upq.isInverted());
        assertEquals("/", upq.getUri());
    }
    
    @Test
    public void complexQuery() {

        Query q = queryParser.parse("(type IN emne && emne:emnekode exists && emne:emnenavn exists" 
                + " && foo:bar not exists && emne:status=gjeldende-versjon && depth=6)" 
                + " AND uri = /studier/emner/jus/* AND name=index.xml AND foo@bar.bing.bong >= baz AND acl ALL");

        assertTrue(q instanceof AndQuery);
        AndQuery aqTop = (AndQuery) q;
        assertEquals(5, aqTop.getQueries().size());

        assertTrue(aqTop.getQueries().get(0) instanceof AndQuery);
        assertTrue(aqTop.getQueries().get(1) instanceof UriPrefixQuery);
        UriPrefixQuery upq = (UriPrefixQuery) aqTop.getQueries().get(1);
        assertEquals("/studier/emner/jus", upq.getUri());
        assertFalse(upq.isIncludeSelf());
        assertFalse(upq.isInverted());

        assertTrue(aqTop.getQueries().get(2) instanceof NameTermQuery);
        NameTermQuery ntq = (NameTermQuery) aqTop.getQueries().get(2);
        assertEquals("index.xml", ntq.getTerm());
        assertEquals(TermOperator.EQ, ntq.getOperator());

        assertTrue(aqTop.getQueries().get(3) instanceof PropertyTermQuery);
        PropertyTermQuery ptq = (PropertyTermQuery) aqTop.getQueries().get(3);
        assertNull(ptq.namespace().getPrefix());
        assertEquals("foo", ptq.name());
        assertEquals("baz", ptq.getTerm());
        assertEquals("bar.bing.bong", ptq.complexValueAttributeSpecifier().get());
        assertEquals(TermOperator.GE, ptq.getOperator());

        assertTrue(aqTop.getQueries().get(4) instanceof AclReadForAllQuery);
        assertFalse(((AclReadForAllQuery)aqTop.getQueries().get(4)).isInverted());
        
        // Level 2, first top node
        AndQuery sub1 = (AndQuery) aqTop.getQueries().get(0);
        assertEquals(6, sub1.getQueries().size());
        assertTrue(sub1.getQueries().get(0) instanceof TypeTermQuery);
        TypeTermQuery ttq = (TypeTermQuery) sub1.getQueries().get(0);
        assertEquals("emne", ttq.getTerm());
        assertEquals(TermOperator.IN, ttq.getOperator());

        assertTrue(sub1.getQueries().get(1) instanceof PropertyExistsQuery);
        PropertyExistsQuery peq = (PropertyExistsQuery) sub1.getQueries().get(1);
        assertFalse(peq.isInverted());
        assertEquals("emne", peq.namespace().getPrefix());
        assertEquals("emnekode", peq.name());

        assertTrue(sub1.getQueries().get(2) instanceof PropertyExistsQuery);
        peq = (PropertyExistsQuery) sub1.getQueries().get(2);
        assertFalse(peq.isInverted());
        assertEquals("emne", peq.namespace().getPrefix());
        assertEquals("emnenavn", peq.name());

        assertTrue(sub1.getQueries().get(3) instanceof PropertyExistsQuery);
        peq = (PropertyExistsQuery) sub1.getQueries().get(3);
        assertTrue(peq.isInverted());
        assertEquals("foo", peq.namespace().getPrefix());
        assertEquals("bar", peq.name());

        assertTrue(sub1.getQueries().get(4) instanceof PropertyTermQuery);
        ptq = (PropertyTermQuery) sub1.getQueries().get(4);
        assertEquals("emne", ptq.namespace().getPrefix());
        assertEquals("status", ptq.name());
        assertEquals(TermOperator.EQ, ptq.getOperator());

        assertTrue(sub1.getQueries().get(5) instanceof UriDepthQuery);
        UriDepthQuery udq = (UriDepthQuery) sub1.getQueries().get(5);
        assertEquals(6, udq.getDepth());

    }

    @Test
    public void propertyWildcardAndPrefixQuery() {

        Query q = queryParser.parse("foo:bar = *suffix || foo:bar =~ PrE\\ fIx* OR foo:bar = \\(prefix\\)*" + " || a!=x* OR a=*x OR a=?x OR a !=~ *fo??o* OR foo:bar =~ *TEXAS\\ HOLD*");

        assertTrue(q instanceof OrQuery);

        List<Query> subs = ((OrQuery) q).getQueries();

        assertEquals(8, subs.size());

        assertTrue(subs.get(0) instanceof PropertyWildcardQuery);
        PropertyWildcardQuery pwq = (PropertyWildcardQuery) subs.get(0);
        assertEquals("foo", pwq.namespace().getPrefix());
        assertEquals("bar", pwq.name());
        assertEquals("*suffix", pwq.getTerm());
        assertEquals(TermOperator.EQ, pwq.getOperator());
        assertFalse(pwq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(1) instanceof PropertyPrefixQuery);
        PropertyPrefixQuery ppq = (PropertyPrefixQuery) subs.get(1);
        assertEquals("foo", ppq.namespace().getPrefix());
        assertEquals("bar", ppq.name());
        assertEquals("PrE fIx", ppq.getTerm());
        assertEquals(TermOperator.EQ_IGNORECASE, ppq.getOperator());
        assertFalse(ppq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(2) instanceof PropertyPrefixQuery);
        ppq = (PropertyPrefixQuery) subs.get(2);
        assertEquals("foo", ppq.namespace().getPrefix());
        assertEquals("bar", ppq.name());
        assertEquals("(prefix)", ppq.getTerm());
        assertEquals(TermOperator.EQ, ppq.getOperator());
        assertFalse(ppq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(3) instanceof PropertyPrefixQuery);
        ppq = (PropertyPrefixQuery) subs.get(3);
        assertEquals(null, ppq.namespace().getPrefix());
        assertEquals("a", ppq.name());
        assertEquals("x", ppq.getTerm());
        assertEquals(TermOperator.NE, ppq.getOperator());
        assertFalse(ppq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(4) instanceof PropertyWildcardQuery);
        pwq = (PropertyWildcardQuery) subs.get(4);
        assertEquals(null, pwq.namespace().getPrefix());
        assertEquals("a", pwq.name());
        assertEquals("*x", pwq.getTerm());
        assertEquals(TermOperator.EQ, pwq.getOperator());
        assertFalse(pwq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(5) instanceof PropertyWildcardQuery);
        pwq = (PropertyWildcardQuery) subs.get(5);
        assertEquals(null, pwq.namespace().getPrefix());
        assertEquals("a", pwq.name());
        assertEquals("?x", pwq.getTerm());
        assertEquals(TermOperator.EQ, pwq.getOperator());
        assertFalse(pwq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(6) instanceof PropertyWildcardQuery);
        pwq = (PropertyWildcardQuery) subs.get(6);
        assertEquals(null, pwq.namespace().getPrefix());
        assertEquals("a", pwq.name());
        assertEquals("*fo??o*", pwq.getTerm());
        assertEquals(TermOperator.NE_IGNORECASE, pwq.getOperator());
        assertFalse(pwq.complexValueAttributeSpecifier().isPresent());

        assertTrue(subs.get(7) instanceof PropertyWildcardQuery);
        pwq = (PropertyWildcardQuery) subs.get(7);
        assertEquals("foo", pwq.namespace().getPrefix());
        assertEquals("bar", pwq.name());
        assertEquals("*TEXAS HOLD*", pwq.getTerm());
        assertEquals(TermOperator.EQ_IGNORECASE, pwq.getOperator());
        assertFalse(pwq.complexValueAttributeSpecifier().isPresent());
    }
}
