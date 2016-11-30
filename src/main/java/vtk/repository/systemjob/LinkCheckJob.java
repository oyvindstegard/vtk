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
package vtk.repository.systemjob;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import vtk.repository.AuthorizationException;
import vtk.repository.Lock;
import vtk.repository.Namespace;
import vtk.repository.Path;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.ResourceNotFoundException;
import vtk.repository.SystemChangeContext;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.PropertySelect;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.query.PropertyTermQuery;
import vtk.repository.search.query.Query;
import vtk.repository.search.query.TermOperator;
import vtk.util.text.Json;
import vtk.util.text.Json.ListContainer;
import vtk.util.text.Json.MapContainer;
import vtk.util.text.JsonBuilder;
import vtk.util.web.LinkTypesPrefixes;
import vtk.web.display.linkcheck.LinkChecker;
import vtk.web.display.linkcheck.LinkChecker.LinkCheckRequest;
import vtk.web.display.linkcheck.LinkChecker.LinkCheckResult;
import vtk.web.service.CanonicalUrlConstructor;
import vtk.web.service.URL;

public class LinkCheckJob extends AbstractResourceJob {
    private PropertyTypeDefinition linkCheckPropDef;
    private PropertyTypeDefinition hrefsPropDef;
    private LinkChecker linkChecker;
    boolean allowCachedResults = true;
    private List<String> blackListConfig;
    private List<Pattern> blackList;
    private int updateBatch = 0;
    private boolean useRepositoryLocks = false;
    private int minRecheckSeconds = 3600;
    
    private CanonicalUrlConstructor urlConstructor;
    
    private static final int MAX_BROKEN_LINKS = 100;   // max number of broken links we bother storing
    private static final int MAX_CHECK_LINKS = 100;    // max number of links to check per resource per round
    
    private static final Logger logger = LoggerFactory.getLogger(LinkCheckJob.class);

    public LinkCheckJob() {
        setSkipLockedResources(false);
    }
    
    @Override
    protected void executeBegin(ExecutionContext ctx) throws Exception {
        ctx.setAttribute("UpdateBatch", 
                new UpdateBatch(ctx.getRepository(), ctx.getToken(), ctx.getSystemChangeContext(),
                        updateBatch, useRepositoryLocks));
    }

    @Override
    protected void executeForResource(Resource resource, ExecutionContext ctx) throws Exception {
        logger.debug("Link check: " + resource.getURI());
        Property hrefsProp = resolveHrefIDs(resource, ctx);
        if (hrefsProp == null) {
            resource.removeProperty(hrefsPropDef);
        }
        else {
            resource.addProperty(hrefsProp);
        }

        Property prop = linkCheck(resource, ctx);
        if (prop == null) {
            // Delete any old stale value
            resource.removeProperty(linkCheckPropDef);
        }
        else {
            resource.addProperty(prop);
        }
        UpdateBatch b = (UpdateBatch)ctx.getAttribute("UpdateBatch");
        b.add(resource);
    }
    
    @Override
    protected void executeEnd(ExecutionContext ctx) throws Exception {
        UpdateBatch b = (UpdateBatch)ctx.getAttribute("UpdateBatch");
        b.flush();
    }

    private Property resolveHrefIDs(final Resource resource, ExecutionContext ctx)
            throws InterruptedException {
        Property hrefsProp = resource.getProperty(hrefsPropDef);
        if (hrefsProp == null) {
            return null;
        }
        
        MapContainer jsonValue = hrefsProp.getJSONValue();
        final URL base = urlConstructor.canonicalUrl(resource).setImmutable();

        if (jsonValue == null || !jsonValue.containsKey("links")) {
            return null;
        }
        
        ListContainer links = jsonValue.arrayValue("links");
        for (int i = 0; i < links.size(); i++) {
            try {
                checkForInterrupt();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            MapContainer hrefObj = links.objectValue(i);
            if (hrefObj.containsKey("url")) {
                String href = (String) hrefObj.get("url");
                URL url = null;

                try {
                    url = base.relativeURL(href);
                }
                catch (Exception e) {
                    logger.debug("Failed to create URL from string: " 
                            + href + ", base: " + base, e);
                    continue;
                }
                // Is the URL "local" (i.e. can we expect to find it in the repository)?
                if (!url.getHost().equals(base.getHost())) {
                    continue;
                }
                
                try {
                    Resource r = ctx.getRepository().
                            retrieve(ctx.getToken(), url.getPath(), false);
                    Property idProp = r.getProperty(
                            Namespace.DEFAULT_NAMESPACE, 
                            PropertyType.EXTERNAL_ID_PROP_NAME);
                    if (idProp != null) {
                        // Update vrtxid if the resource exists
                        hrefObj.put("vrtxid", idProp.getStringValue());
                    }
                }
                catch (ResourceNotFoundException e) {
                    logger.debug("Resource " + href + " (referenced from " 
                            + resource.getURI() + ") not found");
                }
                catch (Exception e) {
                    logger.debug("Failed to retrieve resource " + url.getPath(), e);
                    continue;
                }
            }
            hrefsProp.setJSONValue(jsonValue);
        }
        return hrefsProp;
    }
    
    
    private Property linkCheck(final Resource resource, final ExecutionContext execContext)
            throws InterruptedException {

        SystemChangeContext changeContext = execContext.getSystemChangeContext();
        Property hrefsProp = resource.getProperty(hrefsPropDef);
        if (hrefsProp == null) {
            return null;
        }
        
        Property linkCheckProp = resource.getProperty(linkCheckPropDef);
        
        final LinkCheckState state = LinkCheckState.create(linkCheckProp);
        if (shouldResetState(state, resource, changeContext)) {
            logger.debug("Reset link check state for " + resource.getURI());
            state.brokenLinks.clear();
            state.relocatedLinks.clear();
            state.complete = false;
            state.index = 0;
        }

        if (state.complete) {
            logger.debug("Link check already complete and up to date for " + resource.getURI());
            state.write(linkCheckProp);
            return linkCheckProp;
        }

        logger.debug("Running with link check state: " + state + " for " + resource.getURI());

        // Still supported for JSON properties:
        InputStream linksStream = hrefsProp.getBinaryStream();
        Json.ParseEvents parser = Json.parseAsEvents(linksStream);
        
        final URL base = urlConstructor.canonicalUrl(resource).setImmutable();
        final AtomicLong n = new AtomicLong(0);
        
        try {
            parser.begin(new Json.DefaultHandler() {
                String field = null;
                
                String url = null;
                String vrtxid = null;
                String type = null;

                @Override
                public void endJson() throws IOException {
                    state.complete = true;
                }
                
                @Override
                public boolean beginObject() throws IOException {
                    return true;
                }
                
                 @Override
                 public boolean beginMember(String key) throws IOException {
                     if ("url".equals(key) || "type".equals(key) || "vrtxid".equals(key)) {
                         field = key;
                     }
                     else {
                         field = null;
                     }
                     return true;
                 }

                @Override
                public boolean primitive(Object value) throws IOException {
                    if (value == null) {
                        return true;
                    }
                    String v = value.toString();
                    if (v.length() > 1500) {
                        return true;
                    }
                    if ("url".equals(field)) {
                        url = v;
                    }
                    else if ("type".equals(field)){
                        type = v;
                    }
                    else if ("vrtxid".equals(field)) {
                        vrtxid = v;
                    }
                    return true;
                }
                
                @Override
                public boolean endObject() throws IOException {
                    if (n.getAndIncrement() < state.index) {
                        field = url = type = vrtxid = null;
                        return true;
                    }
                    if (url == null) {
                        field = url = type = vrtxid = null;
                        return true;
                    }
                    if (!shouldCheck(url)) {
                        field = url = type = vrtxid = null;
                        return true;
                    }
                    if ("PROPERTY".equals(type)) {
                        url = base.relativeURL(url).toString();
                    }

                    boolean allowCached = base.getHost().equals(base.relativeURL(url).getHost()) ? 
                            false : allowCachedResults;
                    
                    LinkCheckRequest request = LinkCheckRequest.builder(url, base)
                            .sendReferrer(!resource.isReadRestricted())
                            .allowCached(allowCached)
                            .build();
                    
                    LinkCheckResult result = linkChecker.validate(request);
                    switch (result.getStatus()) {
                    case TIMEOUT:
                        break;
                    case OK:
                        // Check if 'OK' was result of a redirect (relocated resource),
                        // or just a plain '200 OK':
                        URL resourceURL = base.relativeURL(url);
                        if (vrtxid != null && base.getHost().equals(resourceURL.getHost())) {
                            Optional<Path> relocated = findResourceByID(execContext, vrtxid);
                            if (relocated.isPresent() && !relocated.get().equals(resourceURL.getPath())) {
                                Map<String, String> m = new HashMap<>();
                                m.put("link", url);
                                logger.debug("URL " + url + " (referenced from " 
                                        + resource.getURI() + ") has moved, "
                                        + " vrtxid: " + vrtxid + " still valid");
                                m.put("vrtxid", vrtxid);
                                state.relocatedLinks.add(m);
                            }
                        }
                        break;
                    default:
                        // Mark everything except OK and TIMEOUT as broken:
                        Map<String, String> m = new HashMap<>();
                        m.put("link", url);
                        if (type != null) {
                            m.put("type", type);
                        }
                        m.put("status", result.getStatus().toString());
                        state.brokenLinks.add(m);
                    }
                    if (state.brokenLinks.size() >= MAX_BROKEN_LINKS) {
                        return false;
                    }
                    if (state.relocatedLinks.size() >= MAX_BROKEN_LINKS) {
                        return false;
                    }
                    if (n.get()-state.index == MAX_CHECK_LINKS) {
                        return false;
                    }

                    try {
                        checkForInterrupt();
                    }
                    catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }                   
                    field = url = type = null;
                    return true;
                }

                @Override
                public boolean endMember() throws IOException {
                    return true;
                }
            });
            state.timestamp = changeContext.getTimestampFormatted();
            state.index = n.get();
            Property result = linkCheckPropDef.createProperty();
            state.write(result);
            return result;
        }
        catch (Throwable t) {
            if (t.getCause() instanceof InterruptedException) {
                throw ((InterruptedException)t.getCause());
            }
            logger.warn("Error checking links for " + resource.getURI(), t);
            return null;
        }
    }

    private static final Pattern SCHEME =
            Pattern.compile("^([a-z][a-z0-9+.-]+):", Pattern.CASE_INSENSITIVE);
    
    private boolean shouldCheck(String href) {
        if (blackList != null) {
            for (Pattern p: blackList) {
                Matcher m = p.matcher(href);
                if (m.matches()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Skip " + href + ": black-listed");
                    }
                    return false;
                }
            }
        }
        Matcher schemeMatcher = SCHEME.matcher(href);
        if (schemeMatcher.find()) {
            String scheme = schemeMatcher.group(1);
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        }
        return ! href.startsWith(LinkTypesPrefixes.ANCHOR);
    }
    
    private boolean shouldResetState(LinkCheckState state, Resource resource, SystemChangeContext context) {
        if (minRecheckSeconds < 0) {
            logger.debug("Force-resetting link check state (minRecheckSeconds="
                    + minRecheckSeconds + ")");
            return true;
        }
        if (state.timestamp != null) {
            try {
                final long lastCheckRun = SystemChangeContext.parseTimestamp(state.timestamp).getTime();
                final long resourceLastModified = resource.getLastModified().getTime();
                
                // If linkcheck timestamp is older than resource last modified, 
                // we need to invalidate the link check state.
                if (lastCheckRun < resourceLastModified) return true;

                // If complete and more than MIN_RECHECK_SECONDS between now and last run, do check again.
                if (state.complete) {
                    long now = context.getTimestamp().getTime();
                    if (now - lastCheckRun < minRecheckSeconds*1000) {
                        logger.debug("Not long enough since last completed check (min "
                                + minRecheckSeconds + " seconds). Will not reset state.");
                        return false;
                    }
                }
            }
            catch (java.text.ParseException pe) {
                return true;
            }
        }
        
        return state.complete;
    }
    
    private Optional<Path> findResourceByID(ExecutionContext context, String vrtxid) {

        PropertyTypeDefinition idPropDef = context.getRepository()
                .getTypeInfo("resource")
                .getPropertyTypeDefinition(Namespace.DEFAULT_NAMESPACE, 
                        PropertyType.EXTERNAL_ID_PROP_NAME);

        Query query = new PropertyTermQuery(idPropDef, vrtxid, TermOperator.EQ);
        Search search = new Search();
        search.setQuery(query);
        search.setLimit(1);
        search.clearAllFilterFlags();
        search.setSorting(null);
        search.setPropertySelect(PropertySelect.NONE);
        ResultSet result = context.getRepository().search(context.getToken(), search);
        if (result.getTotalHits() == 0) {
            return Optional.empty();
        }
        return Optional.of(result.getResult(0).getURI());
    }
    
    public void refreshBlackList() {
        if (blackListConfig != null) {
            List<Pattern> patterns = new ArrayList<>();
            for (String regexp: blackListConfig) {
                Pattern p = Pattern.compile(regexp);
                patterns.add(p);
            }
            blackList = patterns;
        }
    }

    private static class LinkCheckState {
        private List<Object> brokenLinks = new ArrayList<>();
        private List<Object> relocatedLinks = new ArrayList<>();
        private long index = 0;
        private String timestamp = null;
        private boolean complete = false;
        
        private LinkCheckState() {}

        private static LinkCheckState create(Property statusProp) {
            LinkCheckState s = new LinkCheckState();
            if (statusProp != null) {
                try (InputStream jsonStream = statusProp.getBinaryStream()) {
                    Json.MapContainer status = Json.parseToContainer(jsonStream).asObject();
                    s.complete = "COMPLETE".equals(status.get("status"));
                    for (Object b : status.optArrayValue("brokenLinks", Collections.emptyList())) {
                        s.brokenLinks.add(b);
                    }
                    for (Object b : status.optArrayValue("relocatedLinks", Collections.emptyList())) {
                        s.relocatedLinks.add(b);
                    }
                    s.index = status.optLongValue("index", 0L);
                    s.timestamp = status.optStringValue("timestamp", null);
                }
                catch (Throwable t) { }
            }
            return s;
        }
        
        public void write(Property statusProp) {
            try {
                String jsonString = toJsonString();
                statusProp.setBinaryValue(jsonString.getBytes("utf-8"), "application/json");
            }
            catch (UnsupportedEncodingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        
        private String toJsonString() {
            JsonBuilder jb = new JsonBuilder();
            jb.beginObject()
                    .memberIfNotNull("brokenLinks", brokenLinks)
                    .memberIfNotNull("relocatedLinks", relocatedLinks)
                    .member("status", complete ? "COMPLETE" : "INCOMPLETE")
                    .member("timestamp", timestamp)
                    .member("index", index)
              .endObject();
            return jb.jsonString();
        }
        
        @Override
        public String toString() {
            return toJsonString();
        }
    }
    
    private static class UpdateBatch {
        private final Repository repository;
        private final SystemChangeContext context;
        private final String token;
        private final int batchSize;
        private boolean locking = false;
        private final List<Resource> updateList = new ArrayList<>();

        public UpdateBatch(Repository repository, String token, 
                SystemChangeContext context, int batchSize, boolean locking) {
            this.repository = repository;
            this.token = token;
            this.context = context;
            this.batchSize = batchSize;
            this.locking = locking;
        }

        public void add(Resource resource) {
            updateList.add(resource);
            if (updateList.size() >= batchSize) {
                flush();
            }
        }

        public void flush() {
            if (updateList.size() > 0) {
                logger.debug("Attempting to store " + updateList.size() + " resources");
            }
            if (locking) {
                flushWithLocking();
                return;
            }
            for (Resource r: updateList) {
                try {
                    Resource existing = repository.retrieve(token, r.getURI(), false);
                    if (!existing.getLastModified().equals(r.getLastModified())) {
                        logger.warn("Resource " + r.getURI() 
                        + " was modified during link check, skipping store");
                        continue;
                    }
                    // --> Here be race <--
                    //
                    // Typically we risk AuthorizationException if link-status prop is updated
                    // by something else after last-modified check, and we try to write old value
                    // (the property is uneditable, and an old value will be interpreted by
                    // repo as an attempt to modify). Still, it should be harmless, 
                    // since it will only be an ephemeral problem for props marked as affected
                    // in system change context.
                    r = repository.store(token, r, context);
                }
                catch (ResourceLockedException e) {
                    logger.warn("Resource " + r.getURI() + " was locked by another user, skipping");
                }
                catch (AuthorizationException ae) {
                    logger.warn("Could not store resource " + r.getURI() 
                    + " due to AuthorizationException: " + ae.getMessage());
                }
                catch (Throwable t) {
                    logger.warn("Unable to store resource " + r, t);
                }
            }
            updateList.clear();
        }

        public void flushWithLocking() {
            for (Resource r: updateList) {
                Lock lock = null;
                try {
                    Resource resource = repository.lock(token, r.getURI(), 
                            context.getJobName(), Depth.ZERO, 60, null);
                    lock = resource.getLock();
                    
                    if (!resource.getLastModified().equals(r.getLastModified())) {
                        logger.warn("Resource " + r.getURI() 
                        + " was modified during link check, skipping store");
                        continue;
                    }
                    // Risk AuthorizationException here if resource is stored somewhere else
                    // WITHOUT locking (like resource evaluation does).
                    repository.store(token, r, context);
                }
                catch (ResourceLockedException e) {
                    logger.warn("Resource " + r.getURI() 
                    + " was locked by another user, skipping");
                }
                catch (AuthorizationException ae) {
                    logger.warn("Could not store resource " + r.getURI() 
                    + " due to AuthorizationException: " + ae.getMessage());
                }
                catch (Throwable t) {
                    logger.warn("Unable to store resource " + r, t);
                }
                finally {
                    if (lock != null) {
                        try {
                            repository.unlock(token, r.getURI(), lock.getLockToken());
                        }
                        catch (Exception e) {
                            logger.warn("Unable to unlock resource " + r.getURI(), e);
                        }
                    }
                }
            }
            updateList.clear();
        }
    }
    
    @Required
    public void setHrefsPropDef(PropertyTypeDefinition hrefsPropDef) {
        this.hrefsPropDef = hrefsPropDef;
    }

    @Required
    public void setLinkCheckPropDef(PropertyTypeDefinition linkCheckPropDef) {
        this.linkCheckPropDef = linkCheckPropDef;
    }

    @Required
    public void setLinkChecker(LinkChecker linkChecker) {
        this.linkChecker = linkChecker;
    }
    
    public void setAllowCachedResults(boolean allowCachedResults) {
        this.allowCachedResults = allowCachedResults;
    }
    
    @Required
    public void setCanonicalUrlConstructor(CanonicalUrlConstructor urlConstructor) {
        this.urlConstructor = urlConstructor;
    }
    
    public void setBlackList(List<String> blackList) {
        this.blackListConfig = blackList;
        refreshBlackList();
    }
    
    public void setUseRepositoryLocks(boolean useRepositoryLocks) {
        this.useRepositoryLocks = useRepositoryLocks;
    }
    
    public void setUpdateBatch(int updateBatch) {
        this.updateBatch = updateBatch;
    }

    /**
     * Minimum number of seconds that must have passed since link check
     * was last COMPLETED for resource (without the resource having been
     * modified in the meantime), before a new round of checking is started.
     * 
     * If a negative value is specified, this link check job will always perform 
     * a new link check, regardless of how long since the last time.
     * 
     * @param minRecheckSeconds 
     */
    public void setMinRecheckSeconds(int minRecheckSeconds) {
        this.minRecheckSeconds = minRecheckSeconds;
    }
    
}
