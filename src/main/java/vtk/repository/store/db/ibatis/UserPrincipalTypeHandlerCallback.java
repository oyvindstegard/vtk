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
package vtk.repository.store.db.ibatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.factory.annotation.Required;
import vtk.security.Principal;
import vtk.security.PrincipalFactory;

/**
 * Temporary class for loading {@link Principal} instances at SQL mapper level.
 * This is the wrong thing to do !
 * 
 * <p>This class should be deleted or changed to "UidTypeHandlerCallback" when
 * refactoring principal handling in VTK.
 */
public class UserPrincipalTypeHandlerCallback extends BaseTypeHandler<Principal>{

    private PrincipalFactory pf;
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Principal p, JdbcType jt) throws SQLException {
        ps.setString(1, p.getQualifiedName());
    }

    @Override
    public Principal getNullableResult(ResultSet rs, String column) throws SQLException {
        String qualifiedName = rs.getString(column);
        if (qualifiedName == null) return null;
        return pf.getPrincipal(qualifiedName, Principal.Type.USER);
    }

    @Override
    public Principal getNullableResult(ResultSet rs, int i) throws SQLException {
        String qualifiedName = rs.getString(i);
        if (qualifiedName == null) return null;
        return pf.getPrincipal(qualifiedName, Principal.Type.USER);

    }

    @Override
    public Principal getNullableResult(CallableStatement cs, int i) throws SQLException {
        String qualifiedName = cs.getString(i);
        if (qualifiedName == null) return null;
        return pf.getPrincipal(qualifiedName, Principal.Type.USER);
    }

    @Required
    public void setPrincipalFactory(PrincipalFactory pf) {
        this.pf = pf;
    }
}
