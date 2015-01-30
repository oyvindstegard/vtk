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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import vtk.repository.AuthorizationException;
import vtk.repository.ContentStream;
import vtk.repository.Lock;
import vtk.repository.Property;
import vtk.repository.Repository;
import vtk.repository.Repository.Depth;
import vtk.repository.Resource;
import vtk.repository.ResourceLockedException;
import vtk.repository.SystemChangeContext;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.util.text.Json;
import vtk.util.text.JsonBuilder;
import vtk.util.text.JsonStreamer;
import vtk.util.web.LinkTypesPrefixes;
import vtk.web.display.linkcheck.LinkChecker;
import vtk.web.display.linkcheck.LinkChecker.LinkCheckResult;
import vtk.web.service.CanonicalUrlConstructor;
import vtk.web.service.URL;

public class LinkCheckJob extends AbstractResourceJob {
    
    private PropertyTypeDefinition linkCheckPropDef;
    private PropertyTypeDefinition linksPropDef;
    private LinkChecker linkChecker;
    private List<String> blackListConfig;
    private List<Pattern> blackList;
    private int updateBatch = 0;
    private boolean useRepositoryLocks = false;
    private int minRecheckSeconds = 3600;
    
    private CanonicalUrlConstructor urlConstructor;
    
    private static final int MAX_BROKEN_LINKS = 100;   // max number of broken links we bother storing
    private static final int MAX_CHECK_LINKS = 100;    // max number of links to check per resource per round
    
    private static final Log logger = LogFactory.getLog(LinkCheckJob.class);

    public LinkCheckJob() {
        setSkipLockedResources(false);
    }
    
    @Override
    protected void executeBegin(ExecutionContext ctx) throws Exception {
        ctx.setAttribute("UpdateBatch", 
                new UpdateBatch(ctx.getRepository(), ctx.getToken(), ctx.getSystemChangeContext(),
                        this.updateBatch, this.useRepositoryLocks));
    }

    @Override
    protected void executeForResource(Resource resource, ExecutionContext ctx) throws Exception {
        Property prop = linkCheck(resource, ctx.getSystemChangeContext());
        if (prop != null) {
            resource.addProperty(prop);
        } else {
            // Delete any old stale value
            resource.removeProperty(linkCheckPropDef);
        }
        UpdateBatch b = (UpdateBatch)ctx.getAttribute("UpdateBatch");
        b.add(resource);
    }
    
    @Override
    protected void executeEnd(ExecutionContext ctx) throws Exception {
        UpdateBatch b = (UpdateBatch)ctx.getAttribute("UpdateBatch");
        b.flush();
    }

    private Property linkCheck(final Resource resource, final SystemChangeContext context)
            throws InterruptedException {

        Property linksProp = resource.getProperty(this.linksPropDef);
        if (linksProp == null) {
            return null;
        }
        
        Property linkCheckProp = resource.getProperty(this.linkCheckPropDef);
        
        final LinkCheckState state = LinkCheckState.create(linkCheckProp);
        if (shouldResetState(state, resource, context)) {
            logger.debug("Reset link check state for " + resource.getURI());
            state.brokenLinks.clear();
            state.complete = false;
            state.index = 0;
        }

        if (state.complete) {
            logger.debug("Link check already complete and up to date for " + resource.getURI());
            state.write(linkCheckProp);
            return linkCheckProp;
        }

        logger.debug("Running with link check state: " + state + " for " + resource.getURI());
        
        ContentStream linksStream = linksProp.getBinaryStream();
        Json.ParseEvents parser = Json.parseAsEvents(linksStream.getStream());
        
        final URL base = this.urlConstructor.canonicalUrl(resource).setImmutable();
        final AtomicLong n = new AtomicLong(0);
        
        try {
            parser.begin(new Json.DefaultHandler() {
                String field = null;
                
                String url = null;
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
                     if ("url".equals(key) || "type".equals(key)) {
                         this.field = key;
                     } else {
                         this.field = null;
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
                    if ("url".equals(this.field)) {
                        this.url = v;
                    } else if ("type".equals(this.field)){
                        this.type = v;
                    }
                    return true;
                }
                
                @Override
                public boolean endObject() throws IOException {
                    if (n.getAndIncrement() < state.index) {
                        this.field = this.url = this.type = null;
                        return true;
                    }
                    if (this.url == null) {
                        this.field = this.url = this.type = null;
                        return true;
                        
                    }
                    if (!shouldCheck(this.url)) {
                        this.field = this.url = this.type = null;
                        return true;
                    }
                    if ("PROPERTY".equals(this.type)) {
                        this.url = base.relativeURL(this.url).toString();
                    }
                    
                    LinkCheckResult result = linkChecker.validate(this.url, base, !resource.isReadRestricted());
                    switch (result.getStatus()) {
                    case OK:
                    case TIMEOUT:
                        break;
                    default:
                        // Mark as broken
                        Map<String, Object> m = new HashMap<>();
                        m.put("link", this.url);
                        if (this.type != null) {
                            m.put("type", this.type);
                        }
                        m.put("status", result.getStatus().toString());
                        state.brokenLinks.add(m);
                    }
                    if (state.brokenLinks.size() >= MAX_BROKEN_LINKS) {
                        return false;
                    }
                    if (n.get()-state.index == MAX_CHECK_LINKS) {
                        return false;
                    }

                    try {
                        checkForInterrupt();
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }                   
                    this.field = this.url = this.type = null;
                    return true;
                }

                @Override
                public boolean endMember() throws IOException {
                    return true;
                }
            });
            state.timestamp = context.getTimestampFormatted();
            state.index = n.get();
            Property result = this.linkCheckPropDef.createProperty();
            state.write(result);
            return result;
        } catch (Throwable t) {
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
        if (this.blackList != null) {
            for (Pattern p: this.blackList) {
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
                    if (now - lastCheckRun < this.minRecheckSeconds*1000) {
                        logger.debug("Not long enough since last completed check (min "
                                + this.minRecheckSeconds + " seconds). Will not reset state.");
                        return false;
                    }
                }
            } catch (java.text.ParseException pe) {
                return true;
            }
        }
        
        return state.complete;
    }
    
    public void refreshBlackList() {
        if (this.blackListConfig != null) {
            List<Pattern> patterns = new ArrayList<>();
            for (String regexp: this.blackListConfig) {
                Pattern p = Pattern.compile(regexp);
                patterns.add(p);
            }
            this.blackList = patterns;
        }
    }

    private static class LinkCheckState {
        private List<Object> brokenLinks = new ArrayList<>();
        private long index = 0;
        private String timestamp = null;
        private boolean complete = false;
        
        private LinkCheckState() {}

        @SuppressWarnings("unchecked")
        private static LinkCheckState create(Property statusProp) {
            LinkCheckState s = new LinkCheckState();
            if (statusProp != null) {
                try (InputStream jsonStream = statusProp.getBinaryStream().getStream()) {
                    Json.MapContainer status = Json.parseToContainer(jsonStream).asObject();
                    s.complete = "COMPLETE".equals(status.get("status"));
                    for (Object b : status.optArrayValue("brokenLinks", Collections.emptyList())) {
                        s.brokenLinks.add(b);
                    }
                    s.index = status.optLongValue("index", 0L);
                    s.timestamp = status.optStringValue("timestamp", null);
                } catch (Throwable t) { }
            }
            return s;
        }
        
        public void write(Property statusProp) {
            try {
                String jsonString = toJsonString();
                statusProp.setBinaryValue(jsonString.getBytes("utf-8"), "application/json");
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalStateException(ex);
            }
        }
        
        @SuppressWarnings("unchecked")
        private String toJsonString() {
            JsonBuilder jb = new JsonBuilder();
            jb.beginObject()
                    .memberIfNotNull("brokenLinks", this.brokenLinks)
                    .member("status", this.complete ? "COMPLETE" : "INCOMPLETE")
                    .member("timestamp", this.timestamp)
                    .member("index", this.index)
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

        public UpdateBatch(Repository repository, String token, SystemChangeContext context, int batchSize, boolean locking) {
            this.repository = repository;
            this.token = token;
            this.context = context;
            this.batchSize = batchSize;
            this.locking = locking;
        }

        public void add(Resource resource) {
            this.updateList.add(resource);
            if (this.updateList.size() >= this.batchSize) {
                flush();
            }
        }

        public void flush() {
            if (this.updateList.size() > 0) {
                logger.info("Attempting to store " + this.updateList.size() + " resources");
            }
            if (this.locking) {
                flushWithLocking();
                return;
            }
            for (Resource r: this.updateList) {
                try {
                    Resource existing = repository.retrieve(token, r.getURI(), false);
                    if (!existing.getLastModified().equals(r.getLastModified())) {
                        logger.warn("Resource " + r.getURI() + " was modified during link check, skipping store");
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
                    repository.store(token, r, context);
                } catch (ResourceLockedException e) {
                    logger.warn("Resource " + r.getURI() + " was locked by another user, skipping");
                } catch (AuthorizationException ae) {
                    logger.warn("Could not store resource " + r.getURI() + " due to AuthorizationException: " + ae.getMessage());
                } catch (Throwable t) {
                    logger.warn("Unable to store resource " + r, t);
                }
            }
            this.updateList.clear();
        }

        public void flushWithLocking() {
            for (Resource r: this.updateList) {
                Lock lock = null;
                try {
                    Resource resource = repository.lock(token, r.getURI(), context.getJobName(), Depth.ZERO, 60, null);
                    lock = resource.getLock();
                    if (!resource.getLastModified().equals(r.getLastModified())) {
                        logger.warn("Resource " + r.getURI() + " was modified during link check, skipping store");
                        continue;
                    }
                    // Risk AuthorizationException here if resource is stored somewhere else
                    // WITHOUT locking (like resource evaluation does).
                    repository.store(token, r, context);
                } catch (ResourceLockedException e) {
                    logger.warn("Resource " + r.getURI() + " was locked by another user, skipping");
                } catch (AuthorizationException ae) {
                    logger.warn("Could not store resource " + r.getURI() + " due to AuthorizationException: " + ae.getMessage());
                } catch (Throwable t) {
                    logger.warn("Unable to store resource " + r, t);
                } finally {
                    if (lock != null) {
                        try {
                            repository.unlock(token, r.getURI(), lock.getLockToken());
                        } catch (Exception e) {
                            logger.warn("Unable to unlock resource " + r.getURI(), e);
                        }
                    }
                }
            }
            this.updateList.clear();
        }
    }
    
    @Required
    public void setLinksPropDef(PropertyTypeDefinition linksPropDef) {
        this.linksPropDef = linksPropDef;
    }

    @Required
    public void setLinkCheckPropDef(PropertyTypeDefinition linkCheckPropDef) {
        this.linkCheckPropDef = linkCheckPropDef;
    }

    @Required
    public void setLinkChecker(LinkChecker linkChecker) {
        this.linkChecker = linkChecker;
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
     * @param minRecheckSeconds 
     */
    public void setMinRecheckSeconds(int minRecheckSeconds) {
        this.minRecheckSeconds = minRecheckSeconds;
    }
    
}
