/* Copyright (c) 2016, University of Oslo, Norway
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
package vtk.util.web;

import org.springframework.session.ExpiringSession;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

import vtk.util.cache.SimpleCache;

/**
 * Spring {@link SessionRepository} using a {@link SimpleCache} 
 * as the backing store. Session expiration is assumed to be 
 * handled by the cache.
 */
public class SimpleCacheSessionRepository implements SessionRepository<ExpiringSession> {
    
    private final SimpleCache<String, ExpiringSession> cache;
    
    public SimpleCacheSessionRepository(
            SimpleCache<String, ExpiringSession> cache) {
        this.cache = cache;
    }

    @Override
    public ExpiringSession createSession() {
        return new MapSession();
    }

    @Override
    public void save(ExpiringSession session) {
        cache.put(session.getId(), new MapSession(session));
    }

    @Override
    public ExpiringSession getSession(String id) {
        ExpiringSession session = cache.get(id);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            delete(id);
            return null;
        }
        MapSession result = new MapSession(session);
        result.setLastAccessedTime(System.currentTimeMillis());
        return result;
    }

    @Override
    public void delete(String id) {
        cache.remove(id);
    }

}
