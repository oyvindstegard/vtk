/* Copyright (c) 2006, 2007, 2010, University of Oslo, Norway
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

import java.util.HashMap;
import java.util.Map;

/**
 * This class defines the privileges supported in an ACL. Each privilege maps to
 * one canonical repository action.
 */
public enum Privilege {

    ALL(RepositoryAction.ALL),
    READ_WRITE(RepositoryAction.READ_WRITE),
    READ_WRITE_UNPUBLISHED(RepositoryAction.READ_WRITE_UNPUBLISHED),
    CREATE_WITH_ACL(RepositoryAction.CREATE_WITH_ACL),
    ADD_COMMENT(RepositoryAction.ADD_COMMENT),
    READ(RepositoryAction.READ),
    READ_PROCESSED(RepositoryAction.READ_PROCESSED);
    private final RepositoryAction action;
    private final String name;

    private Privilege(RepositoryAction action) {
        this.action = action;
        this.name = action.getName();
    }
    private static final Map<String, Privilege> NAME_MAP = new HashMap<>();

    static {
        for (Privilege p : values()) {
            NAME_MAP.put(p.name, p);
        }
    }

    public static Privilege forName(String name) {
        Privilege p = NAME_MAP.get(name);
        if (p == null) {
            throw new IllegalArgumentException("Invalid ACL privilege: '" + name + "'");
        }
        return p;
    }

    public static boolean exists(String name) {
        return NAME_MAP.containsKey(name);
    }

    public String getName() {
        return this.name;
    }

    public RepositoryAction getAction() {
        return this.action;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
