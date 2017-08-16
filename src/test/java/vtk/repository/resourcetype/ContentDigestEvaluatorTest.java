/* Copyright (c) 2017, University of Oslo, Norway
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
package vtk.repository.resourcetype;

import java.io.ByteArrayInputStream;
import static org.jmock.AbstractExpectations.equal;
import static org.jmock.AbstractExpectations.returnValue;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;
import static org.junit.Assert.*;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.PropertyEvaluationContext;
import vtk.repository.ResourceImpl;

/**
 *
 */
public class ContentDigestEvaluatorTest {

    private final Mockery context;
    private final Content mockedContent;
    private final Property mockedProperty;
    private final ContentDigestEvaluator evaluator = new ContentDigestEvaluator();

    private final byte[] testbytes = "abc".getBytes();
    private final String testBytesSum = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    public ContentDigestEvaluatorTest() {
        context = new Mockery();
        mockedContent = context.mock(Content.class);
        mockedProperty = context.mock(Property.class);
    }

    @Test
    public void uninitialized() throws Exception {
        final PropertyEvaluationContext evalContext = mockContentChangeContext(mockedContent);

        context.checking(new Expectations() {{
            oneOf(mockedProperty).isValueInitialized();
            will(returnValue(false));

            oneOf(mockedContent).getContentInputStream();
            will(returnValue(new ByteArrayInputStream(testbytes)));

            oneOf(mockedProperty).setStringValue(with(equal(testBytesSum)));
        }});

        assertTrue(evaluator.evaluate(mockedProperty, evalContext));
        context.assertIsSatisfied();
    }

    @Test
    public void uninitializedNoContentChange() throws Exception {
        final PropertyEvaluationContext evalContext = mockPropertiesChangeContext(mockedContent);

        context.checking(new Expectations() {{
            oneOf(mockedProperty).isValueInitialized();
            will(returnValue(false));

            oneOf(mockedContent).getContentInputStream();
            will(returnValue(new ByteArrayInputStream(testbytes)));

            oneOf(mockedProperty).setStringValue(with(equal(testBytesSum)));
        }});

        assertTrue(evaluator.evaluate(mockedProperty, evalContext));
        context.assertIsSatisfied();
    }

    @Test
    public void initialized() throws Exception {
        final PropertyEvaluationContext evalContext = mockContentChangeContext(mockedContent);

        context.checking(new Expectations() {{
            oneOf(mockedProperty).isValueInitialized();
            will(returnValue(true));

            oneOf(mockedContent).getContentInputStream();
            will(returnValue(new ByteArrayInputStream(testbytes)));

            oneOf(mockedProperty).setStringValue(with(equal(testBytesSum)));
        }});

        assertTrue(evaluator.evaluate(mockedProperty, evalContext));
        context.assertIsSatisfied();
    }

    @Test
    public void initializedNoContentChange() throws Exception {
        final PropertyEvaluationContext evalContext = mockPropertiesChangeContext(mockedContent);

        context.checking(new Expectations() {{
            oneOf(mockedProperty).isValueInitialized();
            will(returnValue(true));
        }});

        assertTrue(evaluator.evaluate(mockedProperty, evalContext));
        context.assertIsSatisfied();
    }

    @Test
    public void emptyContent() throws Exception {
        final String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        final PropertyEvaluationContext evalContext = mockContentChangeContext(mockedContent);

        context.checking(new Expectations() {{
            oneOf(mockedProperty).isValueInitialized();
            will(returnValue(false));

            oneOf(mockedContent).getContentInputStream();
            will(returnValue(new ByteArrayInputStream(new byte[0])));

            oneOf(mockedProperty).setStringValue(with(equal(sha256)));
        }});

        assertTrue(evaluator.evaluate(mockedProperty, evalContext));
        context.assertIsSatisfied();
    }

    private PropertyEvaluationContext mockContentChangeContext(Content content) {
        return PropertyEvaluationContext.contentChangeContext(new ResourceImpl(Path.fromString("/a")), null, content);
    }

    private PropertyEvaluationContext mockPropertiesChangeContext(Content content) {
        return PropertyEvaluationContext.propertiesChangeContext(new ResourceImpl(Path.fromString("/a")),
                new ResourceImpl(Path.fromString("/a")), null, content);
    }

}
