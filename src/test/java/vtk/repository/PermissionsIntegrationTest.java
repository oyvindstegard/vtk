/* Copyright (c) 2008, University of Oslo, Norway
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
package vtk.repository;

import java.util.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import vtk.repository.resourcetype.ConstraintViolationException;
import vtk.repository.resourcetype.PropertyType;
import vtk.security.AuthenticationException;
import vtk.security.InvalidPrincipalException;
import vtk.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Enclosed.class)
public class PermissionsIntegrationTest extends RepositoryFixture {
    private static final Repository repository = getRepository(
            new RepositoryArchive("/permissions", "permissions.jar")
    );

    public static class PermissionHierarchy {
        @Test
        public void read_and_writes_gives_comment_right_but_not_admin_rights() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Resource readAndWriteableResource = repository.retrieve(
                    token,
                    Path.fromString("/permissions/rettighetshierarki/vortex-test-les-og-skriv"),
                    false
            );
            assertThat(readAndWriteableResource).isNotNull();
            List<Property> properties = readAndWriteableResource.getProperties();
            assertThat(properties).isNotEmpty();
            Acl acl = readAndWriteableResource.getAcl();
            assertThat(acl).isNotNull();

            List<Path> childURIs = readAndWriteableResource.getChildURIs();
            Path articlePath = childURIs.get(0);
            Resource article = repository.lock(
                    token,
                    articlePath,
                    "permission-test",
                    Repository.Depth.ZERO,
                    5,
                    null,
                    Lock.Type.EXCLUSIVE
            );
            repository.addComment(
                    token, article.getLock().getLockToken(), article, "Test", "Test comment"
            );

            List<Comment> comments = repository.getComments(token, article);
            assertThat(comments).hasSize(2);
            assertThat(comments.get(0).getTitle()).isEqualTo("Test");
            assertThat(comments.get(0).getContent()).isEqualTo("Test comment");
            assertThatThrownBy(
                    () -> repository.storeACL(
                            token, article.getLock().getLockToken(), articlePath, Acl.EMPTY_ACL
                    )
            );
            assertThatThrownBy(
                    () -> repository.deleteACL(token, article.getLock().getLockToken(), articlePath)
            ).isInstanceOf(AuthorizationException.class);
            repository.unlock(token, articlePath, article.getLock().getLockToken());
        }

        @Test
        public void read_do_not_give_read_write() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Path onlyReadPath = Path.fromString("/permissions/rettighetshierarki/vortex-test-les");
            Resource readableResource = repository.retrieve(
                    token, onlyReadPath, false);
            assertThat(readableResource).isNotNull();

            assertThatThrownBy(
                    () -> repository.createDocument(
                            token,
                            null,
                            onlyReadPath.extend("not_allowed.html"),
                            ContentInputSources.empty()
                    )
            ).isInstanceOf(AuthorizationException.class);
        }

        @Test
        public void read_gives_read_processed_but_not_vise_versa() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            String rootToken = newUserToken("root@localhost");

            Path readProcessedPath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-les/les-gir-transformert.php");
            repository.retrieve(
                    token, readProcessedPath, false);
            repository.retrieve(
                    token, readProcessedPath, true);

            Resource phpFile = repository.lock(
                    rootToken,
                    readProcessedPath,
                    "permission-test",
                    Repository.Depth.ZERO,
                    5,
                    null,
                    Lock.Type.EXCLUSIVE
            );
            Acl acl = phpFile.getAcl();
            Principal vortexTest = tokenManager.getPrincipal(token);
            acl = acl.removeEntry(Privilege.READ, vortexTest)
                    .addEntry(Privilege.READ_PROCESSED, vortexTest);
            repository.storeACL(
                    rootToken, phpFile.getLock().getLockToken(), readProcessedPath, acl);
            repository.unlock(rootToken, readProcessedPath, phpFile.getLock().getLockToken());

            repository.retrieve(
                    token, readProcessedPath, true);
            assertThatThrownBy(
                    () ->  repository.retrieve(
                            token, readProcessedPath, false)
            ).isInstanceOf(AuthorizationException.class);

        }

        @Test
        public void admin_gives_read_and_write_and_commenting_rights() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Principal vortexTest = tokenManager.getPrincipal(token);
            Path adminRightPath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/artikkel.html");
            Resource adminRightResource = repository.retrieve(
                    token, adminRightPath, false);

            assertThat(
                    adminRightResource.getAcl().hasPrivilege(Privilege.ALL, vortexTest)
            ).isTrue();
            assertThat(repository.isAuthorized(
                    adminRightResource, RepositoryAction.ADD_COMMENT, vortexTest, false
            )).isTrue();
            assertThat(repository.isAuthorized(
                    adminRightResource, RepositoryAction.READ_WRITE, vortexTest, false
            )).isTrue();
        }

        @Test
        public void can_delete_all_files_if_admin_rights_on_folder() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            String rootToken = newUserToken("root@localhost");
            Path adminRightFolderPath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin");

            Path noRightsPath = adminRightFolderPath.extend("no_rights.html");
            repository.createDocument(
                    rootToken,
                    null,
                    noRightsPath,
                    ContentInputSources.empty()
            );
            repository.storeACL(rootToken, null, noRightsPath, Acl.EMPTY_ACL);

            repository.delete(token, null, noRightsPath, false);
        }

        @Test
        public void admin_gives_edit_comment() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            String rootToken = newUserToken("root@localhost");
            Path adminRightPath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/artikkel.html");
            Resource adminRightResource = repository.retrieve(
                    rootToken, adminRightPath, false);
            repository.addComment(
                    rootToken,
                    null,
                    adminRightResource,
                    "Integrations-test",
                    "Created by integration-test"
            );

            List<Comment> comments = repository.getComments(token, adminRightResource);
            Optional<Comment> comment = comments.stream().filter(
                    (c) -> "Integrations-test".equals(c.getTitle()))
                    .findFirst();
            assertThat(comment.isPresent()).isTrue();
            repository.deleteComment(token, null, adminRightResource, comment.get());
        }

        @Test
        public void acl_is_inherited_if_it_do_not_have_its_own_acl() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Principal userLocalhost = principalFactory.getPrincipal(
                    "user@localhost", Principal.Type.USER
            );
            Path testResourcePath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/test_acl_inheriance.html");
            Resource testResource = repository.createDocument(
                    token,
                    null,
                    testResourcePath,
                    ContentInputSources.empty()
            );
            assertThat(testResource.isInheritedAcl()).isTrue();

            Acl acl = testResource.getAcl().addEntry(Privilege.READ_WRITE, userLocalhost);
            repository.storeACL(token, null, testResourcePath, acl);
            testResource = repository.retrieve(token, testResourcePath, false);
            assertThat(testResource.isInheritedAcl()).isFalse();
            assertThat(
                    testResource.getAcl().hasPrivilege(Privilege.READ_WRITE, userLocalhost)
            ).isTrue();

            repository.delete(
                    token, null, testResourcePath, false
            );
        }

        @Test
        public void cannot_retrieve_without_privileges_even_if_owner() throws Exception {
            String ownerToken = newUserToken("vortex-test@uio.no");
            String rootToken = newUserToken("root@localhost");
            Path testResourcePath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/no_read_for_vortex_test.html"
            );
            Path testCollectionPath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/no_read_for_vortex_test"
            );

            repository.createDocument(
                    ownerToken,
                    null,
                    testResourcePath,
                    ContentInputSources.empty()
            );
            repository.createCollection(
                    ownerToken,
                    null,
                    testCollectionPath
            );
            repository.storeACL(rootToken, null, testResourcePath, Acl.EMPTY_ACL);
            repository.storeACL(rootToken, null, testCollectionPath, Acl.EMPTY_ACL);

            assertThatThrownBy(
                    () -> repository.retrieve(ownerToken, testResourcePath, true)
            ).isInstanceOf(AuthorizationException.class);

            assertThatThrownBy(
                    () -> repository.retrieve(ownerToken, testCollectionPath, true)
            ).isInstanceOf(AuthorizationException.class);

            repository.delete(
                    rootToken, null, testResourcePath, false
            );
            repository.delete(
                    rootToken, null, testCollectionPath, false
            );
        }

        @Test
        public void can_change_owner_only_to_self() throws Exception {
            String ownerToken = newUserToken("vortex-test@uio.no");
            Principal ownerPrincipal = tokenManager.getPrincipal(ownerToken);
            String rootToken = newUserToken("root@localhost");
            Path testResourcePath = Path.fromString(
                    "/permissions/rettighetshierarki/vortex-test-admin/change_owner.html"
            );

            final Resource testResource = repository.createDocument(
                    rootToken,
                    null,
                    testResourcePath,
                    ContentInputSources.empty()
            );

            Property ownerProperty = testResource.getProperty(
                    Namespace.DEFAULT_NAMESPACE, PropertyType.OWNER_PROP_NAME
            );
            assertThat(ownerProperty.getValue().getPrincipalValue())
                    .isEqualTo(testResource.getOwner());

            ownerProperty.setPrincipalValue(
                    principalFactory.getPrincipal("user@localhost", Principal.Type.USER)
            );
            testResource.addProperty(ownerProperty);
            assertThatThrownBy(
                    () -> repository.store(ownerToken, null, testResource)
            ).isInstanceOf(ConstraintViolationException.class);

            ownerProperty.setPrincipalValue(ownerPrincipal);
            testResource.addProperty(ownerProperty);
            repository.store(ownerToken, null, testResource);
            Resource updatedResource = repository.retrieve(ownerToken, testResourcePath, false);
            assertThat(updatedResource.getOwner()).isEqualTo(ownerPrincipal);

            repository.delete(
                    rootToken, null, testResourcePath, false
            );
        }

    }

    public static class Copy {

        @Test
        public void can_only_copy_if_user_has_read_access_to_the_whole_subtree() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Path cannotReadRootPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/kilde-vortex-test-ikke-les" +
                            "/vortex-test-ikke-les-rot"
            );
            Path cannotReadSubPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/kilde-vortex-test-ikke-les" +
                            "/vortex-test-ikke-les-lenger-ned"
            );
            Path targetPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/maal-ok"
            );

            assertThatThrownBy(
                    () -> repository.copy(
                            token,
                            null,
                            cannotReadRootPath,
                            targetPath.extend("vortex-test-ikke-les-rot"),
                            false,
                            false)
            ).isInstanceOf(AuthorizationException.class);

            assertThatThrownBy(
                    () -> repository.copy(
                            token,
                            null,
                            cannotReadSubPath,
                            targetPath.extend("vortex-test-ikke-les-lenger-ned"),
                            false,
                            false)
            ).isInstanceOf(AuthorizationException.class);
        }

        @Test
        public void copy_requires_write_on_target_folder() throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Path copyMeNoACLPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/kilde-ok/kopier-meg-ikke-acl"
            );
            Path sourcePath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/kilde-ok"
            );
            Path noWriteAccessPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/maal-vortex-test-ikke-skriv"
            );
            Path writeAccessPath = Path.fromString(
                    "/permissions/kopier/kopier-rettigheter/maal-ok"
            );

            assertThatThrownBy(
                    () -> repository.copy(
                            token,
                            null,
                            copyMeNoACLPath,
                            noWriteAccessPath.extend("kopier-meg-ikke-acl"),
                            false,
                            false)
            ).isInstanceOf(AuthorizationException.class);

            Path sourceTarget = writeAccessPath.extend("kilde-ok");
            repository.copy(
                    token,
                    null,
                    sourcePath,
                    sourceTarget,
                    false,
                    false
            );

            repository.delete(
                    token, null, sourceTarget, false);
        }
    }

    public static class Move {

        @Test
        public void move_requires_write_on_target_if_source_do_not_have_its_own_acl()
                throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Path noReadPath = Path.fromString(
                    "/permissions/flytt/ikke-acl-kilde/kilde-vortex-test-ikke-les"
            );
            Path moveMePath = Path.fromString(
                    "/permissions/flytt/ikke-acl-kilde/kilde-ok/flytt-meg"
            );
            Path noWritePath = Path.fromString(
                    "/permissions/flytt/ikke-acl-kilde/maal-vortex-test-ikke-skriv"
            );
            Path writePath = Path.fromString(
                    "/permissions/flytt/ikke-acl-kilde/maal-ok"
            );

            assertThatThrownBy(
                    () -> repository.retrieve(token, noReadPath, false)
            ).isInstanceOf(AuthorizationException.class);

            assertThatThrownBy(
                    () -> repository.move(
                            token,
                            null,
                            moveMePath,
                            noWritePath.extend("flytt-meg"),
                            false)
            ).isInstanceOf(AuthorizationException.class);

            repository.move(
                    token,
                    null,
                    moveMePath,
                    writePath.extend("flytt-meg"),
                    false
            );

        }

        @Test
        public void move_require_admin_on_target_if_source_have_its_own_acl()
                throws Exception {
            String token = newUserToken("vortex-test@uio.no");
            Path cannotMovePath = Path.fromString(
                    "/permissions/flytt/har-acl-kilde/kilde-vortex-test-ikke-unbind"
            );
            Path sourcePath = Path.fromString(
                    "/permissions/flytt/har-acl-kilde/kilde-ok"
            );
            Path hasAdminPath = Path.fromString(
                    "/permissions/flytt/har-acl-kilde/maal-ok"
            );
            Path noAdminPath = Path.fromString(
                    "/permissions/flytt/har-acl-kilde/maal-vortex-test-ikke-admin"
            );

            assertThatThrownBy(
                    () -> repository.move(
                            token,
                            null,
                            cannotMovePath,
                            hasAdminPath.extend("cannot-move"),
                            false)
            ).isInstanceOf(AuthorizationException.class);

            assertThatThrownBy(
                    () -> repository.move(
                            token,
                            null,
                            sourcePath.extend("flytt-meg-les-alt"),
                            noAdminPath.extend("flytt-meg-les-alt"),
                            false)
            ).isInstanceOf(AuthorizationException.class);

            String[] folderNames = {"flytt-meg-ikke-les", "flytt-meg-les-alt", "flytt-meg-les-noe"};
            for (String folderName : folderNames) {
                repository.move(
                        token,
                        null,
                        sourcePath.extend(folderName),
                        hasAdminPath.extend(folderName),
                        false
                );
            }
        }

    }

    public static class Blacklist {
        private String rootToken;
        private Path webidResourcePath;
        private Resource webidResource;

        @Before
        public void setUp() throws Exception {
            this.rootToken = newUserToken("root@localhost");
            this.webidResourcePath = Path.fromString(
                    "/permissions/alle-webid/tilgang-for-webidebrukeren-systemtest.html"
            );

            this.webidResource = repository.lock(
                    rootToken,
                    webidResourcePath,
                    "permission-test",
                    Repository.Depth.ZERO,
                    5,
                    null,
                    Lock.Type.EXCLUSIVE
            );
        }

        @After
        public void tearDown() throws Exception {
            repository.unlock(
                    rootToken,
                    webidResourcePath,
                    webidResource.getLock().getLockToken()
            );
        }

        @Test
        public void users_not_allowed_to_have_admin_rights() throws Exception {
            final Principal pseudoAll = principalFactory.getPrincipal(
                    "pseudo:all", Principal.Type.PSEUDO
            );
            assertPrincipalIsNotAllowedToHavePrivilege(pseudoAll, Privilege.ALL);

            final Principal webidUser = principalFactory.getPrincipal(
                    "user@webid.uio.no", Principal.Type.USER
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidUser, Privilege.ALL);

            final Principal webidAdminGroup = principalFactory.getPrincipal(
                    "admin@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidAdminGroup, Privilege.ALL);
        }

        @Test
        public void users_not_allowed_to_have_read_write_rights() throws Exception {
            final Principal pseudoAll = principalFactory.getPrincipal(
                    "pseudo:all", Principal.Type.PSEUDO
            );
            assertPrincipalIsNotAllowedToHavePrivilege(pseudoAll, Privilege.READ_WRITE);

            final Principal webidUser = principalFactory.getPrincipal(
                    "user@webid.uio.no", Principal.Type.USER
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidUser, Privilege.READ_WRITE);

            final Principal webidAllGroup = principalFactory.getPrincipal(
                    "alle@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidAllGroup, Privilege.READ_WRITE);

            final Principal webidAdminGroup = principalFactory.getPrincipal(
                    "admin@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsAllowedToHavePrivilege(webidAdminGroup, Privilege.READ_WRITE);
        }

        @Test
        public void users_not_allowed_to_have_read_rights() throws Exception {
            final Principal pseudoAll = principalFactory.getPrincipal(
                    "pseudo:all", Principal.Type.PSEUDO
            );
            assertPrincipalIsAllowedToHavePrivilege(pseudoAll, Privilege.READ);

            final Principal webidUser = principalFactory.getPrincipal(
                    "user@webid.uio.no", Principal.Type.USER
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidUser, Privilege.READ);

            final Principal webidAllGroup = principalFactory.getPrincipal(
                    "alle@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidAllGroup, Privilege.READ);

            final Principal webidAdminGroup = principalFactory.getPrincipal(
                    "admin@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsAllowedToHavePrivilege(webidAdminGroup, Privilege.READ);
        }

        @Test
        public void users_not_allowed_to_have_comment_rights() throws Exception {
            final Principal pseudoAll = principalFactory.getPrincipal(
                    "pseudo:all", Principal.Type.PSEUDO
            );
            assertPrincipalIsNotAllowedToHavePrivilege(pseudoAll, Privilege.ADD_COMMENT);

            final Principal webidUser = principalFactory.getPrincipal(
                    "user@webid.uio.no", Principal.Type.USER
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidUser, Privilege.ADD_COMMENT);

            final Principal webidAllGroup = principalFactory.getPrincipal(
                    "alle@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsAllowedToHavePrivilege(webidAllGroup, Privilege.ADD_COMMENT);
        }

        @Test
        public void users_not_allowed_to_have_read_processed_rights() throws Exception {
            final Principal pseudoAll = principalFactory.getPrincipal(
                    "pseudo:all", Principal.Type.PSEUDO
            );
            assertPrincipalIsAllowedToHavePrivilege(pseudoAll, Privilege.READ_PROCESSED);

            final Principal webidUser = principalFactory.getPrincipal(
                    "user@webid.uio.no", Principal.Type.USER
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidUser, Privilege.READ_PROCESSED);

            final Principal webidAllGroup = principalFactory.getPrincipal(
                    "alle@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsNotAllowedToHavePrivilege(webidAllGroup, Privilege.READ_PROCESSED);

            final Principal webidAdminGroup = principalFactory.getPrincipal(
                    "admin@webid.uio.no", Principal.Type.GROUP
            );
            assertPrincipalIsAllowedToHavePrivilege(webidAdminGroup, Privilege.READ_PROCESSED);
        }

        private void assertPrincipalIsNotAllowedToHavePrivilege(
                Principal principal, Privilege privilege
        ) {
            if (Principal.Type.PSEUDO.equals(principal.getType())) {
                assertThatThrownBy(
                        () -> webidResource.getAcl().addEntry(privilege, principal)
                ).isInstanceOf(IllegalArgumentException.class);
                return;
            }
            final Acl acl = webidResource.getAcl().addEntry(privilege, principal);
            assertThatThrownBy(
                    () -> repository.storeACL(
                            rootToken,
                            webidResource.getLock().getLockToken(),
                            webidResourcePath,
                            acl
                    )
            ).isInstanceOf(InvalidPrincipalException.class);
        }

        private void assertPrincipalIsAllowedToHavePrivilege(
                Principal principal, Privilege privilege
        ) throws Exception {
            final Acl acl = webidResource.getAcl().addEntry(privilege, principal);
            repository.storeACL(
                    rootToken,
                    webidResource.getLock().getLockToken(),
                    webidResourcePath,
                    acl
            );
        }

    }

    public static class ReadSource {

        @Test
        public void cannot_read_source_with_read_processed_privilege()
                throws Exception
        {
            Path fileWithReadProcessedPrivilege = Path.fromString(
                    "/permissions/portal-kun-les-transformert.xml"
            );
            repository.retrieve(
                    null, fileWithReadProcessedPrivilege,true);
            assertThatThrownBy(
                    () -> repository.retrieve(
                            null, fileWithReadProcessedPrivilege,false)
            ).isInstanceOf(AuthenticationException.class);
        }

        @Test
        public void can_read_source_with_read_privilege() throws Exception {
            Path fileWithReadPrivilege = Path.fromString("/permissions/portal.xml");
            repository.retrieve(null, fileWithReadPrivilege,true);
            repository.retrieve(null, fileWithReadPrivilege,false);
        }

    }
}
