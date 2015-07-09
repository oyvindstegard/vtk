/* Copyright (c) 2015, University of Oslo, Norway
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
package vtk.security.web.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import vtk.security.AuthenticationException;
import vtk.security.AuthenticationProcessingException;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.security.PrincipalStore;
import vtk.web.service.Assertion;

/**
 * Proxy authentication handler. 
 *
 * <p>Authenticates the request first using regular HTTP/Basic
 * authentication (as the <em>proxy user</em>), and then performs a second
 * authentication based on a request parameter containing the <em>target
 * user</em></p>
 * 
 * <p>Typical usage is to configure a (trusted) proxy user that allows
 * another system to authenticate as target users on its behalf.</p>
 * 
 * <p>The set of valid proxy users and target users is limited using
 * regular expressions, and an optional {@link PrincipalStore} can be
 * used to validate (otherwise syntactically correct) target users.</p>
 * 
 * <p>A set of {@link Assertion assertions} can also be configured to limit 
 * the scope in which this authentication handler attempts to perform 
 * authentication.</p>
 */
public class ProxyAuthenticationHandler extends HttpBasicAuthenticationHandler {
    
    private Pattern proxyUserExpression = null;
    private Pattern targetUserExpression = null;
    private PrincipalStore targetPrincipalStore = null;
    private String requestParam = null;
    
    private List<Assertion> assertions = null;

    /**
     * Constructs a new proxy authentication handler.
     * @param proxyUserExpression expression to limit the set of valid proxy users
     * @param targetUserExpression expression to limit the set of valid target users
     * @param requestParam the name of the request parameter containing the target user id
     */
    public ProxyAuthenticationHandler(String proxyUserExpression, String targetUserExpression, String requestParam) {
        super();
        this.proxyUserExpression = Pattern.compile(proxyUserExpression);
        this.targetUserExpression = Pattern.compile(targetUserExpression);
        if (requestParam == null) throw new NullPointerException();
        this.requestParam = requestParam;
    }

    @Override
    public boolean isRecognizedAuthenticationRequest(HttpServletRequest req)
            throws AuthenticationProcessingException {
        if (!super.isRecognizedAuthenticationRequest(req)) return false;
        String sysuser = getUserName(req);
        if (!proxyUserExpression.matcher(sysuser).matches()) return false;
        if (req.getParameter(requestParam) == null) return false;
        if (assertions != null) {
            for (Assertion a: assertions)
                if (!a.matches(req, null, null)) return false;
        }
        return true;
    }

    @Override
    public AuthResult authenticate(HttpServletRequest request)
            throws AuthenticationProcessingException, AuthenticationException {
        super.authenticate(request);
        String targetUid = request.getParameter(requestParam);
        if (!targetUserExpression.matcher(targetUid).matches()) 
            throw new AuthenticationException("Unknown principal: " + targetUid);
        
        if (targetPrincipalStore != null) {
            Principal p = new PrincipalImpl(targetUid, Principal.Type.USER);
            if (!targetPrincipalStore.validatePrincipal(p)) 
                throw new AuthenticationException("Unknown principal: " + targetUid);
        }
        return new AuthResult(targetUid);
    }

    public void setAssertions(List<Assertion> assertions) {
        if (assertions == null || assertions.isEmpty()) return;
        this.assertions = new ArrayList<>(assertions);
    }
    
    public void setTargetPrincipalStore(PrincipalStore targetPrincipalStore) {
        this.targetPrincipalStore = targetPrincipalStore;
    }
    
}
