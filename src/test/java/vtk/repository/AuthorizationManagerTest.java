/* Copyright (c) 2012, University of Oslo, Norway
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Test;

import vtk.repository.store.DataAccessException;
import vtk.repository.store.DataAccessor;
import vtk.security.AuthenticationException;
import vtk.security.GroupStore;
import vtk.security.MatchingGroupStore;
import vtk.security.MatchingPrincipalStore;
import vtk.security.Principal;
import vtk.security.PrincipalImpl;
import vtk.security.PrincipalManager;
import vtk.security.PrincipalManagerImpl;
import vtk.security.roles.RoleManager;

public class AuthorizationManagerTest {
    
    @Test
    public void test01() throws Exception {
        TestHarness harness = new TestHarness("auth-manager-test01.json");
        harness.run();
    }

    private static class TestHarness {
        
        private TestDataAccessor dao;
        private PrincipalManager principalManager;
        private AuthorizationManager authorizationManager;
        private List<TestAssertion> assertions;
        
        public TestHarness(String classPathRef) throws Exception {
            init(classPathRef);
        }
        
        public void run() throws Exception {
            for (TestAssertion assertion: this.assertions) {
                System.out.println("Running test: " + assertion.name());
            }
        }
        
        private void init(String classPathRef) throws Exception {
            InputStream in = getClass().getResourceAsStream(classPathRef);
            if (in == null) {
                throw new IllegalStateException("File '" + classPathRef + "' cannot be found");
            }
            Reader reader = new InputStreamReader(in);
            JSONObject toplevel = (JSONObject) JSONValue.parse(reader);
            
            JSONObject principalManager = (JSONObject) toplevel.get("principal-manager");
            initPrincipalManager(principalManager.toJSONString());
            
            JSONObject repo = (JSONObject) toplevel.get("resources");
            
            List<Path> list = new ArrayList<>();
            for (Object key: repo.keySet()) {
                list.add(Path.fromString(key.toString()));
            }
            Collections.sort(list, new Comparator<Path>() {
                @Override
                public int compare(Path arg0, Path arg1) {
                    
                    if (arg0.isAncestorOf(arg1)) {
                        return -1;
                    }
                    else if (arg1.isAncestorOf(arg0)) {
                        return 1;
                    }
                    else if (arg0.getDepth() < arg1.getDepth()) {
                        return -1;
                    }
                    else if (arg0.equals(arg1)) {
                        return 0;
                    }
                    else {
                        return 1;
                    }
                }
            });
            StringBuilder sb = new StringBuilder("{");
            for (Path key: list) {
                sb.append('"').append(key).append('"').append(" : ");
                sb.append(((JSONObject) repo.get(key.toString())).toJSONString()); 
            }
            sb.append("}");
            
            initDataAccessor(sb.toString());
            
            initAuthManager();
            
            JSONObject assertions = (JSONObject) toplevel.get("assertions");
            initAssertions(assertions.toJSONString());

        }

        private void initPrincipalManager(String json) throws Exception {
            JSONObject obj = (JSONObject) JSONValue.parse(json);

            JSONObject principalStoreJson = (JSONObject) obj.get("matching-principal-store");
            String users = principalStoreJson.get("pattern").toString();
            
            JSONObject groupStoreJson = (JSONObject) obj.get("matching-group-store");
            @SuppressWarnings("unchecked")
            Map<String, String> groups = 
                    (Map<String, String>) groupStoreJson.get("map");
            
            
            MatchingPrincipalStore ps = new MatchingPrincipalStore();
            ps.setPattern(users);
            
            MatchingGroupStore gs = new MatchingGroupStore();
            gs.setGroupsMap(groups);
            
            PrincipalManagerImpl pm = new PrincipalManagerImpl();
            pm.setPrincipalStore(ps);
            pm.setGroupStores(Arrays.asList(new GroupStore[]{gs}));
            
            this.principalManager = pm;
        }
        

        private void initDataAccessor(String json) throws Exception {
            Reader reader = new StringReader(json);
            TestResourceFactory factory = new TestResourceFactory();
            final TestDataAccessor dao = new TestDataAccessor();

            factory.load(reader, new TestResourceFactory.Consumer() {
                @Override
                public boolean resource(ResourceImpl resource) {
                    dao.put(resource);
                    return true;
                }
                @Override
                public void end() {
                }
            });
            this.dao = dao;
        }
        
        private void initAuthManager() {
            AuthorizationManager mgr = new AuthorizationManager();
            mgr.setDao(dao);
            mgr.setPrincipalManager(this.principalManager);
            mgr.setRoleManager(new RoleManager());
            this.authorizationManager = mgr;
        }
        
        private void initAssertions(String json) throws Exception {
            JSONObject object = (JSONObject) JSONValue.parse(json);
            List<TestAssertion> assertions = new ArrayList<>();
            for (Object key: object.keySet()) {
                JSONObject val = (JSONObject) object.get(key);
                if (val.containsKey("method")) {
                    assertions.add(createMethodAssertion(key.toString(), val));
                }
                else {
                    assertions.add(createActionAssertion(key.toString(), val));
                }
            }
            this.assertions = assertions;
        }
        
        private MethodAssertion createMethodAssertion(String id, JSONObject val) {
            requireFields(val, "method", "expected-outcome");
            
            Optional<String> description = Optional.ofNullable(val.get("description")).map(Object::toString);
            
            Object o = val.get("method");
            if (!(o instanceof List<?>)) {
                throw new IllegalArgumentException("Field 'method' must be an array");
            }
            
            List<?> list = (List<?>) o;
            if (list.size() < 1) {
                throw new IllegalArgumentException("Field 'method' is empty");
            }
            String name = list.get(0).toString();
            
            Method method = null;
            for (Method m: AuthorizationManager.class.getMethods()) {
                if (m.getName().equals(name)) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new IllegalArgumentException("Method '" + name + "' not found");
            }
            int idx = 0;
            List<Object> actualParams = new ArrayList<>();
            for (Class<?> c: method.getParameterTypes()) {
                idx++;
                if (idx > list.size() - 1) {
                    throw new IllegalArgumentException("Too few actual parameters supplied: " + list);
                }
                Object obj = list.get(idx);
                if (c == Principal.class) {
                    Principal principal = new PrincipalImpl(obj.toString(), Principal.Type.USER);
                    if (principal != null && !principalManager.validatePrincipal(principal)) {
                        throw new IllegalArgumentException("Invalid principal: " + principal);
                    }
                    actualParams.add(principal);
                }
                else if (c == Path.class) {
                    Path uri = Path.fromString(obj.toString());
                    actualParams.add(uri);
                }
                else if (c == boolean.class) {
                    actualParams.add(Boolean.valueOf(obj.toString()));
                    
                }
                else {
                    throw new IllegalArgumentException("Unsupported parameter class: " + c);
                }
            }
            Object[] args = actualParams.toArray(new Object[actualParams.size()]);
            Outcome outcome = Outcome.valueOf(val.get("expected-outcome").toString());
            
            return new MethodAssertion(id, method, args, outcome, description);
        }

        private ActionAssertion createActionAssertion(String id, JSONObject val) {
            requireFields(val, "uri", "principal", "repository-action", "expected-outcome");
            Optional<String> description = Optional.ofNullable(val.get("description")).map(Object::toString);
            Path[] uris = null;

            Object o = val.get("uri");
            if (o == null) {
                throw new IllegalArgumentException("Field 'uri' of " 
                        + val + " is NULL");
            }
            else if (o instanceof List<?>) {
                List<Path> list = new ArrayList<>();
                for (Object elem: (List<?>) o) {
                    list.add(Path.fromString(elem.toString()));
                }
                uris = list.toArray(new Path[list.size()]);
            }
            else {
                uris = new Path[]{ Path.fromString(o.toString())};
            }
            
            Principal[] principals = null;
            
            o = val.get("principal");
            if (o == null) {
                principals = new Principal[] {null};
            }
            else if (o instanceof List<?>) {
                List<Principal> list = new ArrayList<>();
                for (Object elem: (List<?>) o) {
                    list.add(new PrincipalImpl(elem.toString(), Principal.Type.USER));
                }
                principals = list.toArray(new Principal[list.size()]);
            }
            else {
                principals = new Principal[] { new PrincipalImpl(o.toString(), Principal.Type.USER) };
            }
            for (Principal principal: principals) {
                if (principal != null && !principalManager.validatePrincipal(principal)) {
                    throw new IllegalArgumentException("Invalid principal: " + principal);
                }
            }
            
            RepositoryAction[] actions = null;
            
            o = val.get("repository-action");
            if (o == null) {
                throw new IllegalArgumentException("Field 'repository-action' of " 
                        + val + " is NULL");
            }
            else if (o instanceof List<?>) {
                List<RepositoryAction> list = new ArrayList<>();
                for (Object elem: (List<?>) o) {
                    list.add(RepositoryAction.valueOf(elem.toString()));
                }
                actions = list.toArray(new RepositoryAction[list.size()]);
            }
            else {
                actions = new RepositoryAction[]{ RepositoryAction.valueOf(o.toString()) };
            }
            
            Outcome outcome = Outcome.valueOf(val.get("expected-outcome").toString());
            ActionAssertion assertion = new ActionAssertion(
                    id, uris, principals, actions, outcome, description);
            return assertion;
            
        }
        
        private static enum Outcome {
            SUCCESS, FAILURE;
        }
        
        
        private static interface TestAssertion {
            public String name();
            public Optional<String> description();
            public void test(AuthorizationManager authManager) throws Exception;
        }
        
        private static class MethodAssertion implements TestAssertion {
            private String id;
            private Method method;
            private Object[] args;
            private Outcome outcome;
            private Optional<String> description;
            
            public String name() { 
                return id; 
            }
            
            public Optional<String> description() {
                return description;
            }
            
            public MethodAssertion(String id, Method method, Object[] args, 
                    Outcome outcome, Optional<String> description) {
                this.id = id;
                this.method = method;
                this.args = args;
                this.outcome = outcome;
                this.description = description;
            }
            public void test(AuthorizationManager authManager) throws Exception {
                switch (outcome) {
                case SUCCESS:
                    try {
                        invoke(authManager);
                        return;
                    }
                    catch (Throwable t) {
                        throw new Exception(failureMessage(Outcome.FAILURE), t);
                    }
                case FAILURE:
                    try {
                        invoke(authManager);
                    }
                    catch (AuthenticationException | AuthorizationException e) {
                        return;
                    }
                    catch (Throwable t) {
                        throw new Exception("Unhandled throwable: " + t);
                    }
                    throw new Exception(failureMessage(Outcome.SUCCESS));
                }
            }
            
            private void invoke(AuthorizationManager authManager) throws Throwable {
                try {
                    method.invoke(authManager, args);
                }
                catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            }
            
            @Override
            public String toString() {
                return this.id;
            }
            
            private String failureMessage(Outcome actual) {
                String message = "Assertion " + this + " had unexpected outcome " + actual;
                Optional<String> description = description();
                if (description.isPresent()) {
                    message = message + ": " + description.get();
                }
                return message;
            }
        }
        
        private static class ActionAssertion implements TestAssertion {
            private String id;
            private Optional<String> description;
            private Path[] uris;
            private Principal[] principals;
            private RepositoryAction[] actions;
            private Outcome outcome;
            
            public ActionAssertion(String id, Path[] uris, Principal[] principals,
                    RepositoryAction[] actions, Outcome outcome, Optional<String> description) {
                this.id = id;
                this.uris = uris;
                this.principals = principals;
                this.actions = actions;
                this.outcome = outcome;
                this.description = description;
            }
            
            public String name() {
                return id;
            }
            
            public Optional<String> description() {
                return description;
            }
            
            public void test(AuthorizationManager authManager) throws Exception {
                switch (outcome) {
                case SUCCESS:
                    for (Path uri: uris) {
                        for (Principal principal: principals) {
                            for (RepositoryAction action: actions) {
                                try {
                                    authManager.authorizeAction(uri, action, principal);
                                }
                                catch (Throwable t) {
                                    String message = "Assertion " + this 
                                            + ": combination (" + action + ", " 
                                            + uri + ", " + principal 
                                            + ") had outcome FAILURE"
                                            + description.map(d -> ": " + d).orElse("");
                                    throw new Exception(message, t);
                                }
                            }
                        }
                    }
                    return;
                case FAILURE:
                    for (Path uri: uris) {
                        for (Principal principal: principals) {
                            for (RepositoryAction action: actions) {
                                try {
                                    authManager.authorizeAction(uri, action, principal);
                                    String message = "Assertion " + this 
                                            + ": combination (" + action + ", " 
                                            + uri + ", " + principal 
                                            + ") had outcome SUCCESS"
                                            + description.map(d -> ": " + d).orElse("");
                                    throw new Exception(message);
                                }
                                catch (AuthenticationException | AuthorizationException e) { }
                            }
                        }
                    }
                    return;
                }
            }
            
            @Override
            public String toString() {
                return this.id;
            }
        }
    }
    
    private static class TestDataAccessor implements DataAccessor {

        private Map<Path, ResourceImpl> tree = new HashMap<>();
        
        public void put(ResourceImpl resource) {
            if (resource == null) {
                throw new IllegalArgumentException("Resource is NULL");
            }
            if (resource.getAcl() == null && resource.getURI() == Path.ROOT) {
                throw new IllegalArgumentException("Root resource must have an ACL");
            }
            if (resource.getURI() != Path.ROOT) {
                for (Path p: resource.getURI().getAncestors()) {
                    if (!this.tree.containsKey(p)) {
                        throw new IllegalArgumentException("Cannot add '" + resource.getURI() 
                                + "': '" + p + "' does not exist");
                    }
                }
                ResourceImpl parent = this.tree.get(resource.getURI().getParent());
                parent.addChildURI(resource.getURI());
            }
            
            this.tree.put(resource.getURI(), resource);
        }

        @Override
        public Path getResourcePath(int id) throws DataAccessException {
            Optional<ResourceImpl> resource = tree.values().stream()
                    .filter(r -> r.getNumericId() == id).findFirst();
            if (resource.isPresent()) {
                return resource.get().getURI();
            }
            return null;
        }

        @Override
        public ResourceImpl load(Path uri) throws DataAccessException {
            if (!tree.containsKey(uri)) {
                return null;
            }
            
            ResourceImpl r = null;
            Acl acl = null;
            for (Path p: uri.getPaths()) {
                r = this.tree.get(p);
                if (!r.isInheritedAcl()) {
                    acl = r.getAcl();
                }
            }
            try {
                ResourceImpl clone = (ResourceImpl) r.clone();
                clone.setAcl(acl);
                return clone;
            }
            catch (Throwable t) { 
                return null;
            }
        }
        
        @Override
        public ResourceImpl[] loadChildren(ResourceImpl parent)
                throws DataAccessException {
            List<ResourceImpl> children = new ArrayList<>();
            for (Path childURI: parent.getChildURIs()) {
                ResourceImpl child = load(childURI);
                if (child == null) {
                    throw new IllegalStateException("Inconsistent state: child " 
                            + childURI + " does not exist");
                }
                children.add(child);
            }
            return children.toArray(new ResourceImpl[children.size()]);
        }

        @Override
        public ResourceImpl store(ResourceImpl r) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceImpl storeACL(ResourceImpl r) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceImpl storeLock(ResourceImpl r)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(ResourceImpl resource) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markDeleted(ResourceImpl resource, ResourceImpl parent,
                Principal principal, String trashID) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecoverableResource> getRecoverableResources(
                int parentResourceId) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceImpl recover(Path parentUri,
                RecoverableResource recoverableResource)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteRecoverable(RecoverableResource recoverableResource)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecoverableResource> getTrashCanOverdue(int overdueLimit)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecoverableResource> getTrashCanOrphans()
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteExpiredLocks(Date expireDate)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceImpl copy(ResourceImpl resource,
                ResourceImpl destParent, PropertySet newResource,
                boolean copyACLs, PropertySet fixedProperties, Set<String> uncopyableProperties)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceImpl move(ResourceImpl resource, ResourceImpl newResource)
                throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path[] discoverLocks(Path uri) throws DataAccessException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path[] discoverACLs(Path uri) throws DataAccessException {
            List<Path> result = new ArrayList<>();
            for (Path p: this.tree.keySet()) {
                if (uri.isAncestorOf(p) || uri.equals(p)) {
                    ResourceImpl r = this.tree.get(p);
                    if (r.getAcl() != null) {
                        result.add(p);
                    }
                }
            }
            return result.toArray(new Path[result.size()]);
        }

        @Override
        public Set<Principal> discoverGroups() throws DataAccessException {
            throw new UnsupportedOperationException();
        }
        
    }

    private static void requireFields(JSONObject obj, String...fields) {
        for (String field: fields) {
            if (!obj.containsKey(field)) {
                throw new IllegalArgumentException("Required field '" + field + "' not in object " + obj);
            }
        }
    }
}
