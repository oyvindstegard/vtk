package org.vortikal.web.actions.permissions;

import java.util.ArrayList;
import java.util.List;

import org.vortikal.security.Principal;
import org.vortikal.security.PrincipalFactory;
import org.vortikal.security.Principal.Type;
import org.vortikal.web.actions.copymove.CopyMoveToSelectedFolderController;

import sun.security.acl.PrincipalImpl;

import junit.framework.TestCase;

public class ACLEditControllerTest extends TestCase {
    
    private ACLEditController controller;
    private PrincipalFactory principalFactory;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        controller = new ACLEditController();
        principalFactory = new PrincipalFactory();
    }
    
    public void testExtractAndCheckShortcuts() {

        List<Principal> authorizedUsers = new ArrayList<Principal>();
        List<Principal> authorizedGroups = new ArrayList<Principal>();
        List<String> shortcuts = new ArrayList<String>();
        
        // ACLs
        Principal userAll = PrincipalFactory.ALL;
        authorizedUsers.add(userAll);
        Principal groupAll = principalFactory.getPrincipal("alle@uio.no", Type.GROUP);
        authorizedGroups.add(groupAll);
        
        // Shortcuts
        shortcuts.add("user:pseudo:all");
        shortcuts.add("group:alle@uio.no");
        shortcuts.add("group:alle@feide.uio.no");
        shortcuts.add("group:alle@webid.uio.no");
        
        String[][] extractedShortcuts = controller.extractAndCheckShortcuts(authorizedUsers, authorizedGroups, shortcuts);
        
        assertEquals("user:pseudo:all", extractedShortcuts[0][0]);
        assertEquals("checked", extractedShortcuts[0][1]);
        assertEquals("group:alle@uio.no", extractedShortcuts[1][0]);
        assertEquals("checked", extractedShortcuts[1][1]);
        assertEquals("group:alle@feide.uio.no", extractedShortcuts[2][0]);
        assertEquals("", extractedShortcuts[2][1]);
        assertEquals("group:alle@webid.uio.no", extractedShortcuts[3][0]);
        assertEquals("", extractedShortcuts[3][1]);
        
    }

}
