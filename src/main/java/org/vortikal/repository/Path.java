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
package org.vortikal.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;

/**
 * The <code>Path</code> class represents a set of 
 * string segments starting with and separated by 
 * a slash character.
 * 
 * An example of this is the path <code>/a/b</code>, 
 * which contains the three elements <code>/</code>, 
 * <code>a</code> and <code>b</code>. 
 * 
 */
public final class Path implements Comparable<Path> {
    
    private static final int MAX_LENGTH = 1500;
    
    /**
     * Represents the root path (<code>/</code>).
     */
    public static final Path ROOT = new Path("/");
	
	private String path;
	private List<String> elements;
    private List<Path> paths;
    
	private Path(String path) {
        this.path = path;
        this.elements = new ArrayList<String>();
        this.paths = new ArrayList<Path>();
        if ("/".equals(this.path)) {
            this.elements.add("/");
            this.paths.add(this);
            return;
        }
        StringTokenizer st = new StringTokenizer(this.path, "/");
        while (st.hasMoreTokens()) {
            String name = st.nextToken();
            this.elements.add(name);
            StringBuilder ancestorString = new StringBuilder();
            
            for (int i = 0; i < this.elements.size() - 1; i++) {
                String s = this.elements.get(i);
                ancestorString.append("/").append(s);
            }
            
            if (ancestorString.length() > 0) { 
                this.paths.add(instance(ancestorString.toString()));
            }
        }
        this.elements.add(0, "/");
        this.paths.add(0, instance("/"));
        this.paths.add(this);
	}
	
	private static Path instance(String path) {
	    // Use cached instance of '/'
	    if (ROOT.path.equals(path)) return ROOT;
	    return new Path(path);
	}
	
    /**
     * Instantiates a path from a string representation. 
     * This is the only way to create path objects.
     * @param path the string representation (must be a well-formed path)
     * @throws IllegalArgumentException if the parameter is not well-formed
     */
	public static Path fromString(String path) {
		if (path == null || "".equals(path.trim())) {
			throw new IllegalArgumentException("Invalid path: '" + path + "'");
		}
		if (!path.startsWith("/")) {
			throw new IllegalArgumentException("Invalid path: '" + path + "'");
		}

		if (StringUtils.isBlank(path)
                || path.length() >= MAX_LENGTH
                || !path.startsWith("/")
                || path.contains("//") || path.contains("/../")
                || path.endsWith("/..") || path.endsWith("/.")
                || (!path.equals("/") && path.endsWith("/"))) {

            throw new IllegalArgumentException("Invalid path: '" + path + "'");
        }
		return instance(path);
	}

	
	public static Path fromStringRemoveTrailingSlash(String path) {
        if (path.endsWith("/") && !path.equals("/"))
            path = path.substring(0, path.length() - 1);
        return fromString(path);
	}
	
	/**
	 * Gets the string representation of this path. 
	 * This is the same string used to 
	 * construct this path. 
	 */
	public String toString() {
	    return this.path;
	}


	/**
	 * Compares this path to another path.
	 */
	public int compareTo(Path path) {
	    return this.path.compareTo(path.path);
	}
	
	/**
	 * Gets the name (last element) of this path.
	 * @return the last element of the path
	 */
	public String getName() {
		return this.elements.get(this.elements.size() - 1);
	}

	/**
	 * Returns true if this path is the root path (<code>/</code>).
	 * @return whether this path is the root path
	 */
	public boolean isRoot() {
	    return this.path.equals("/");
	}
	
	/**
	 * Gets the depth of this path. The depth is 
	 * equal to the number of elements in the path 
	 * minus one.
	 */
	public int getDepth() {
		return this.elements.size() - 1;
	}
	
	/**
	 * Returns whether this path contains another path, 
	 * i.e. that this path is one of the ancestors of or 
	 * is the same path as the other.
	 * @param other the other path
	 * @return true if this path contains the other, false otherwise.
	 */
	public boolean isAncestorOf(Path other) {
	    if (other.elements.size() <= this.elements.size()) {
	        // If other path's depth is less than or equal to this, we cannot
	        // be ancestor.
	        return false;
	    } 
	    
	    // Compare elements from the root down to last element of this path
	    for (int i = 0; i < this.elements.size(); i++) {
            if (!this.elements.get(i).equals(other.elements.get(i))) {
                return false;
            }
        }
	    
	    return true;
	}
	
	/**
	 * Gets the elements of this path as a list.
	 * @return the path elements
	 */
	public List<String> getElements() {
		return Collections.unmodifiableList(this.elements);
	}
	
	/**
	 * Returns the paths that comprise this path, as a list. 
	 * For example, for the path <code>/a/b/c</code>, this 
	 * method would return the following paths:
	 * <code>/</code>, <code>/a</code>, <code>/a/b</code> and 
	 * <code>/a/b/c</code>. 
	 * @return the paths that make up this path
	 */
	public List<Path> getPaths() {
	    return Collections.unmodifiableList(this.paths);
	}
	
	/**
	 * Gets the ancestor paths as a list. This method is identical to 
	 * {@link #getPaths} except that the last element (the path itself)
	 * is omitted.
	 * @return
	 */
	public List<Path> getAncestors() {
	    if (this.path.equals("/")) {
	        return Collections.emptyList();
	    } else {
	        List<Path> result = this.paths.subList(0, this.paths.size() - 1); 
	        return Collections.unmodifiableList(result);
	    }
	}
	
	/**
	 * Gets the parent path of this path.
	 * @return the parent path, or <code>null</code> if 
	 * this path is the root path.
	 */
	public Path getParent() {
	    if (this.paths.size() == 1) {
	        return null;
	    }
	    return this.paths.get(this.paths.size() - 2);
	}
	
	/**
	 * Extends this path with a sub-path. For example, the path 
	 * <code>/a</code> when extended with the sub-path <code>b/c</code>
	 * would produce the resulting path </code>/a/b/c</code>.
	 * @param subPath
	 * @return the extended path
	 */
	public Path extend(String subPath) {
	    if ("/".equals(this.path)) {
	        return fromString(this.path + subPath);
	    } else {
	        return fromString(this.path + "/" + subPath);
	    }
	}
	
	public boolean equals(Object o) {
	    if (!(o instanceof Path)) return false;
	    return ((Path)o).path.equals(this.path);
	}
	
	public int hashCode() {
	    return this.path.hashCode();
	}
}
