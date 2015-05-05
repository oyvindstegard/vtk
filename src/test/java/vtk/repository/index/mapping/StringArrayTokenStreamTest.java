package vtk.repository.index.mapping;


import com.ibm.icu.text.CollationKey;
import com.ibm.icu.text.Collator;
import java.util.Locale;
import static org.junit.Assert.*;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.collation.ICUCollationAttributeFactory;
import org.apache.lucene.util.BytesRef;

import org.junit.Test;

public class StringArrayTokenStreamTest {

    @Test
    public void oneValueAsCollationKey() throws Exception {
        String value = "1-value";
        
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        TokenStream stream = new StringArrayTokenStream(new ICUCollationAttributeFactory(collator), value);
        
        assertTrue(stream.incrementToken());
        
        TermToBytesRefAttribute ttb = stream.getAttribute(TermToBytesRefAttribute.class);
        BytesRef indexTerm = ttb.getBytesRef();
        ttb.fillBytesRef();
        
        CollationKey key = collator.getCollationKey(value);
        BytesRef keyBytes = new BytesRef(key.toByteArray());
        assertTrue("Unexpected collation key bytes", keyBytes.bytesEquals(indexTerm));
    }
    
    @Test
    public void multipleValues() throws Exception {

        String[] values = new String[]{"foo", "bar", "hepp", "hopp"};
        TokenStream stream = new StringArrayTokenStream(values);

        assertTrue(stream.incrementToken());

        CharTermAttribute ta = stream.getAttribute(CharTermAttribute.class);
//        OffsetAttribute oa = stream.getAttribute(OffsetAttribute.class);
        assertEquals("foo", ta.toString());
//        assertEquals(0, oa.startOffset());
//        assertEquals(3, oa.endOffset());

        assertTrue(stream.incrementToken());

        ta = stream.getAttribute(CharTermAttribute.class);
//        oa = stream.getAttribute(OffsetAttribute.class);
        assertEquals("bar", ta.toString());
//        assertEquals(3, oa.startOffset());
//        assertEquals(6, oa.endOffset());

        assertTrue(stream.incrementToken());

        ta = stream.getAttribute(CharTermAttribute.class);
//        oa = stream.getAttribute(OffsetAttribute.class);
        assertEquals("hepp", ta.toString());
//        assertEquals(6, oa.startOffset());
//        assertEquals(10, oa.endOffset());

        assertTrue(stream.incrementToken());

        ta = stream.getAttribute(CharTermAttribute.class);
//        oa = stream.getAttribute(OffsetAttribute.class);
        assertEquals("hopp", ta.toString());
//        assertEquals(10, oa.startOffset());
//        assertEquals(14, oa.endOffset());

        assertFalse(stream.incrementToken());
    }

    @Test
    public void zeroValues() throws Exception {
        TokenStream stream = new StringArrayTokenStream(new String[]{});
        assertFalse(stream.incrementToken());
    }

}
