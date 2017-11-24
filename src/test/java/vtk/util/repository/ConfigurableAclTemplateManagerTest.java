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
package vtk.util.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

import vtk.repository.Acl;
import vtk.repository.Path;
import vtk.repository.Privilege;
import vtk.repository.ResourceNotFoundException;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.util.Result;
import vtk.util.io.IO;

public class ConfigurableAclTemplateManagerTest {

    @Before
    public void setUp() throws Exception { }
    
    @Test
    public void test1() {
        Function<Path,Result<Acl>> baseAcls = uri -> {
            if (Path.ROOT.equals(uri)) {
                return Result.success(Acl.EMPTY_ACL
                        .addEntry(Privilege.ALL, 
                                new PrincipalImpl("admin@localhost", Principal.Type.USER))
                        .addEntry(Privilege.READ_WRITE, 
                                new PrincipalImpl("user1@localhost", Principal.Type.USER))
                        .addEntry(Privilege.READ, 
                                new PrincipalImpl("all", Principal.Type.PSEUDO)));
            }
            else if (Path.fromString("/vrtx").equals(uri)) {
                return Result.success(Acl.EMPTY_ACL
                        .addEntry(Privilege.ALL, 
                                new PrincipalImpl("admin@localhost", Principal.Type.USER))
                        .addEntry(Privilege.ALL, 
                                new PrincipalImpl("user1@localhost", Principal.Type.USER))
                        .addEntry(Privilege.READ_WRITE, 
                                new PrincipalImpl("user1@localhost", Principal.Type.USER)));

            }
            return Result.failure(new ResourceNotFoundException(uri));
        };
        String inputTemplate = "{\n" + 
                "  \"/\" :\n" + 
                "     { \"acl\" { \"all\": { \"users\": [ \"${user}\" ] }},\n" + 
                "       \"type\": \"additive\" }, \n" +
                "  \"/vrtx\" :\n" + 
                "     { \"acl\" { \"read\" : { \"groups\" : [ \"some-group\" ] },\n" + 
                "             \"all\": { \"users\": [ \"${user}\" ] }}}}"
                ;

        ConfigurableAclTemplateManager mgr = manager(inputTemplate, baseAcls);

        assertFalse(mgr.template(Path.ROOT.extend("not-found")).isPresent());
        
        Principal user = new PrincipalImpl("user2@localhost", Principal.Type.USER);
        
        Optional<AclTemplate> root = mgr.template(Path.ROOT);
        assertTrue(root.isPresent());
        Acl acl = root.get().resolve(user);
        Acl expected = Acl.EMPTY_ACL
                .addEntry(Privilege.READ_WRITE, 
                        new PrincipalImpl("user1@localhost", Principal.Type.USER))
                .addEntry(Privilege.ALL, user)
                .addEntry(Privilege.ALL, 
                        new PrincipalImpl("admin@localhost", Principal.Type.USER))
                .addEntry(Privilege.READ, 
                        new PrincipalImpl("all", Principal.Type.PSEUDO));
        
        assertEquals(acl, expected);


        Optional<AclTemplate> vrtx = mgr.template(Path.fromString("/vrtx"));
        assertTrue(vrtx.isPresent());
        acl = vrtx.get().resolve(user);
        expected = Acl.EMPTY_ACL
                .addEntry(Privilege.READ, new PrincipalImpl("some-group", Principal.Type.GROUP))
                .addEntry(Privilege.ALL, user);
        assertEquals(acl, expected);
    }

    
    @Test
    public void testInvalidConfig() {
        String inputTemplate = "{ [='invali:d }}JSON] }";
        
        ConfigurableAclTemplateManager mgr = manager(inputTemplate, 
                uri -> Result.success(Acl.EMPTY_ACL));
        try {
            mgr.template(Path.ROOT);
            throw new RuntimeException("Should fail");
        }
        catch (IllegalStateException expected) { }

        
        inputTemplate = "{ \"not_a_path\" : { \"acl\": { }}}";
        mgr = manager(inputTemplate, uri -> Result.success(Acl.EMPTY_ACL));
        try {
            mgr.template(Path.ROOT);
            throw new RuntimeException("Should fail");
        }
        catch (IllegalStateException expected) { }
        
        inputTemplate = "{\n" + 
                "  \"/\" :\n" + 
                "     { \"acl\" { \"read\" : { \"groups\" : [ \"some-group\" ] },\n" + 
                "             \"all\": { \"users\": [ \"${user}\" ] }},\n" + 
                "       \"type\": \"invalid_type\" }}";
        
        mgr = manager(inputTemplate, uri -> Result.success(Acl.EMPTY_ACL));
        try {
            mgr.template(Path.ROOT);
            throw new RuntimeException("Should fail");
        }
        catch (IllegalStateException expected) { }
        
        
        inputTemplate = "{\n" + 
                "  \"/\" :\n" + 
                "     { \"acl\" { \"unknown_privilege\" : { \"groups\" : [ \"some-group\" ] }}}}";
        
        mgr = manager(inputTemplate, uri -> Result.success(Acl.EMPTY_ACL));
        try {
            mgr.template(Path.ROOT);
            throw new RuntimeException("Should fail");
        }
        catch (IllegalStateException expected) { }
    }
    
    private ConfigurableAclTemplateManager manager(String inputTemplate, Function<Path,Result<Acl>> baseAcls) {
        Result<InputStream> input = Result.attempt(() -> {
            try {
                return IO.stringStream(inputTemplate);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        
        ConfigurableAclTemplateManager mgr = 
                new ConfigurableAclTemplateManager(baseAcls);
        mgr.accept(input);
        return mgr;
    }

}
