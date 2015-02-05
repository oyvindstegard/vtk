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
package vtk.web.actions.copymove;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import javax.servlet.http.HttpSession;

import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.ModelAndView;

import vtk.repository.Path;
import vtk.web.AbstractControllerTest;

/**
 * Exercise the CopyMoveController.
 * 
 * @author Gyrd Thane Lange
 */
public class CopyMoveToSelectedFolderControllerTest extends AbstractControllerTest {
	
	private CopyMoveToSelectedFolderController controller = null;
	private HttpSession mockHttpSession = new MockHttpSession();
	private ModelAndView result = null;
	private CopyMoveSessionBean sessionBeanAfterRequest = null;
	
	@Override
	protected Path getRequestPath() {
		return Path.fromString("/somewhere/?action=something");
	}
	
	@Override
    @Before
	public void setUp() throws Exception {
		super.setUp();
		controller = new CopyMoveToSelectedFolderController();
	}

    @Test
	public void testNormal() throws Exception {
		doRequest(false, false);
		assertNull("session bean should be cleared", sessionBeanAfterRequest);
	}

    @Test
	public void testCancel() throws Exception {
		doRequest(true, false);
		assertNotNull("session bean should be intact", sessionBeanAfterRequest);
	}

    @Test
	public void testClear() throws Exception {
		doRequest(false, true);
		assertNull("session bean should be cleared", sessionBeanAfterRequest);
	}
	
	private void doRequest(final boolean withCancel, final boolean withClear) throws Exception {
		// setup
		context.checking(new Expectations() {{
			//allowing(mockRequest).getSession(); will(returnValue(mockHttpSession));
			allowing(mockRequest).getSession(true); will(returnValue(mockHttpSession));
                        allowing(mockRequest).getParameter("revision");
                        allowing(mockRequest).getParameter("vrtxPreviewUnpublished");
                        
			//allowing(mockRequest).getSession(false); will(returnValue(mockHttpSession));
			//allowing(mockRequest).getMethod(); will(returnValue("POST"));
			//allowing(mockRequest).getParameterNames(); will(returnValue(new Vector<String>().elements()));
			allowing(mockRequest).getParameter("overwrite"); will(returnValue(null));
			allowing(mockRequest).getParameter("existing-skipped-files"); will(returnValue(null));
            
			allowing(mockRequest).getParameter("cancel-action"); will(returnValue(withCancel ? "" : null));
			allowing(mockRequest).getParameter("clear-action"); will(returnValue(withClear ? "" : null));
		}});
		
		CopyMoveSessionBean sessionBean = new CopyMoveSessionBean();
//		This will force an exception in the "normal" path,
//		Useful to show that this path is actually taken.
//		sessionBean.setAction("copy");
//		List<String> fileNames = new ArrayList<String>();
//		fileNames.add("/does/not/exist");
//		sessionBean.setFilesToBeCopied(fileNames);
		mockHttpSession.setAttribute(CopyMoveToSelectedFolderController.COPYMOVE_SESSION_ATTRIBUTE, sessionBean);
		
		result = controller.handleRequest(mockRequest, mockResponse);
		sessionBeanAfterRequest = (CopyMoveSessionBean) mockHttpSession.getAttribute(CopyMoveToSelectedFolderController.COPYMOVE_SESSION_ATTRIBUTE);

		assertNotNull(result);
	}
	
}
