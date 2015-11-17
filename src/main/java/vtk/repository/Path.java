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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * The <code>Path</code> class represents a set of string segments starting with
 * and separated by a slash character.
 * 
 * An example of this is the path <code>/a/b</code>, which contains the three
 * elements <code>/</code>, <code>a</code> and <code>b</code>.
 * 
 */
public final class Path implements Comparable<Path>, Serializable {

    private static final long serialVersionUID = -7752433857207156353L;

    public static final int MAX_LENGTH = 1500;

    /**
     * Represents the root path (<code>/</code>).
     */
    public static final Path ROOT = new Path();

    private final String path;
    private final String name;

    // Private constructor for the ROOT path (only called at class init/load)
    private Path() {
        this.path = this.name = "/";
    }

    // Private constructor for other paths (only called from
    // Path#instance(String))
    private Path(String path) {
        this.path = path;
        this.name = path.substring(path.lastIndexOf('/') + 1);
    }

    // This is the only method from which Path instances can be created.
    private static Path instance(String path) {
        // Use singleton instance for '/'
        if (ROOT.path.equals(path))
            return ROOT;

        return new Path(path);
    }

    /**
     * Instantiates a path from a string representation. This is the only way to
     * create path objects.
     * 
     * @param path
     *            the string representation (must be a well-formed path)
     * @throws IllegalArgumentException
     *             if the parameter is not well-formed
     */
    public static Path fromString(String path) {
        if (path == null
              || path.length() >= MAX_LENGTH
              || !path.startsWith("/")
              || path.contains("//")
              || path.contains("/../")
              || path.endsWith("/..")
              || path.endsWith("/.")
              || (!path.equals("/") && path.endsWith("/"))) {

            throw new IllegalArgumentException("Invalid path: '" + path + "'");
        }
        return instance(path);
    }

    /**
     * Snips any number of consecutive trailing slashes from input path
     * before constructing the real path.
     * @see #fromString(String)
     */
    public static Path fromStringWithTrailingSlash(String path) {
        int i = path.length()-1;
        while (i > 0 && path.charAt(i) == '/') {
            --i;
        }
        return fromString(path.substring(0, i+1));
    }

    /**
     * Gets the string representation of this path. This is the same string used
     * to construct this path.
     */
    @Override
    public String toString() {
        return this.path;
    }

    /**
     * Compares this path to another path lexicographically.
     * 
     * @param path other path
     * @see Comparable#compareTo(java.lang.Object) 
     */
    @Override
    public int compareTo(Path path) {
        return this.path.compareTo(path.path);
    }

    /**
     * Gets the name (last element) of this path.
     * 
     * @return the last element of the path
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns true if this path is the root path (<code>/</code>).
     * 
     * @return whether this path is the root path
     */
    public boolean isRoot() {
        return (this == ROOT);
    }

    /**
     * Gets the depth of this path. The depth is equal to the number of elements
     * in the path minus one.
     */
    public int getDepth() {
        if (isRoot())
            return 0;

        int depth = 1, idx = 0;
        while ((idx = this.path.indexOf('/', idx + 1)) != -1)
            ++depth;

        return depth;
    }
    
    /**
     * Returns whether this path contains another path, i.e. that this path is
     * one of the ancestors of the other.
     * 
     * @param other
     *            the other path
     * @return true if this path contains the other, false otherwise.
     */
    public boolean isAncestorOf(Path other) {
        if (other.path.length() <= this.path.length()) {
            return false;
        }
        
        if (this == ROOT && other != ROOT) return true;
        
        int i;
        for (i=0; i < this.path.length(); i++) {
            if (this.path.charAt(i) != other.path.charAt(i)) {
                return false;
            }
        }
        
        if (other.path.charAt(i) == '/') {
            return true;
        }
        
        return false;
    }

    /**
     * Gets the elements of this path as a list.
     * 
     * @return the path elements
     */
    public List<String> getElements() {
        return this.elements();
    }

    /**
     * Returns the paths that comprise this path, as a list. For example, for
     * the path <code>/a/b/c</code>, this method would return the following
     * paths: <code>/</code>, <code>/a</code>, <code>/a/b</code> and
     * <code>/a/b/c</code>.
     * 
     * @return the paths that make up this path
     */
    public List<Path> getPaths() {
        return this.paths();
    }

    /**
     * Gets the ancestor paths as a list. This method is identical to
     * {@link #getPaths} except that the last element (the path itself) is
     * omitted.
     * 
     * @return list of paths to all ancestors
     */
    public List<Path> getAncestors() {
        if (this == ROOT) {
            return Collections.emptyList();
        } else {
            List<Path> paths = this.paths();
            return paths.subList(0, paths.size() - 1);
        }
    }

    /**
     * Gets one of the paths that comprise this path on a given level. 
     * If the supplied level is 0, the root path is returned. If the 
     * supplied level is equal to the number of elements in this path 
     * minus 1, this path itself is returned.
     * 
     * @return the path on the given level
     * @throws IllegalArgumentException
     *             if the supplied level is outside the bounds of the element
     *             list of this path
     */
    public Path getPath(int level) {
        List<Path> paths = this.paths();
        if (level < 0 || level >= paths.size()) {
            throw new IllegalArgumentException("Index out of bounds: " + level);
        }
        return paths.get(level);
    }

    /**
     * Gets the parent path of this path.
     * 
     * @return the parent path, or <code>null</code> if this path is the root
     *         path.
     */
    public Path getParent() {
        if (isRoot())
            return null;

        int lastSlashIdx = this.path.lastIndexOf('/');
        if (lastSlashIdx == 0)
            return ROOT;

        return instance(this.path.substring(0, lastSlashIdx));
    }

    /**
     * Extends this path with a sub-path. For example, the path <code>/a</code>
     * when extended with the sub-path <code>b/c</code> would produce the
     * resulting path </code>/a/b/c</code>. The parameter may not contain
     * <code>"../"<code> or <code>"./"</code> sequences - use {@link #expand(java.lang.String)}
     * instead for such cases.
      * 
     * @param subPath
     *            the (relative) path with which to extend this path
     * @return the extended path
     */
    public Path extend(String subPath) {
        if (isRoot()) {
            return fromString(this.path + subPath);
        } else {
            return fromString(this.path + "/" + subPath);
        }
    }

    /**
     * Extends this path with a sub-path, and also expands <code>..</code> and
     * <code>./</code>. For example, the path <code>/a/b/c</code> when extended
     * with the sub-path <code>../d</code> would produce the resulting path
     * </code>/a/b/d</code>.
     * 
     * @param expansion
     *            the (relative) path with which to extend this path
     * @return the expanded path, or <code>null</code> if the expansion string
     *         contains too many <code>../</code> sequences
     */
    public Path expand(String expansion) {
        if (StringUtils.isBlank(expansion)) {
            throw new IllegalArgumentException(
                    "Expand: " + this + ": illegal argument ["
                    + expansion + "]");
        }
        if (expansion.trim().startsWith("/")) {
            throw new IllegalArgumentException(
                    "Expand: " + this + ": illegal argument [" 
                    + expansion + "]");
        }
        Path cur = this;
        StringBuilder segment = new StringBuilder();
        int length = expansion.length();
        
        for (int i = 0; i < length; i++) {
            if (cur == null) {
                return null;
            }
            char c = expansion.charAt(i);
            if (c == '/' || i == length - 1) {
                if (c != '/') {
                    segment.append(c);
                }

                if (segment.length() == 2 
                        && segment.charAt(0) == '.' && segment.charAt(1) == '.') { // dot-dot-<slash-or-end>
                    cur = cur.getParent();
                    segment.setLength(0);
                } else if (segment.length() == 1 && segment.charAt(0) == '.') {   // dot-<slash-or-end>
                    segment.setLength(0);
                } else {
                    cur = cur.extend(segment.toString());
                    segment.setLength(0);
                }
            } else {
                segment.append(c);
            }
        }
        return cur;
    }
    
    /**
     * Returns longest common path between this path and another.
     * @param otherPath the other
     * @return the longest common path
     */
    public Path common(Path otherPath) {
        if (otherPath == null || otherPath.isRoot() || this.isRoot()) {
            return Path.ROOT;
        }
        if (this.isAncestorOf(otherPath) || this.equals(otherPath)) {
            return this;
        }
        Path common = Path.ROOT;
        for (int i = 1; i <= otherPath.getAncestors().size(); i++) {
            if (!otherPath.getPath(i).equals(this.getPath(i))) {
                break;
            }
            common = this.getPath(i);
        }
        return common;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Path))
            return false;
        return ((Path) o).path.equals(this.path);
    }

    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

    private List<String> elements() {
        List<String> elements = new ArrayList<>();
        elements.add("/");
        if (this == ROOT) {
            return elements;
        }

        int start = 1, i;
        for (i=2; i < this.path.length(); i++) {
            if (this.path.charAt(i) == '/') {
                elements.add(this.path.substring(start, i));
                start = i + 1;
            }
        }
        elements.add(this.path.substring(start));
        
        return elements;
    }
    
    private List<Path> paths() {
        List<Path> paths = new ArrayList<>();
        paths.add(ROOT);
        
        if (this == ROOT) {
            return paths;
        }
        
        for (int i=1; i < this.path.length(); i++) {
            if (this.path.charAt(i) == '/') {
                paths.add(instance(this.path.substring(0, i)));
            }
        }

        paths.add(this);
        
        return paths;
    }
}
