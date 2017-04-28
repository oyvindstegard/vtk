/* Copyright (c) 2006, University of Oslo, Norway
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
package vtk.repository.search;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import vtk.repository.PropertySet;

/**
 * Simple cached result set.
 */
public class ResultSetImpl implements ResultSet {

    protected final List<PropertySet> results;
    protected int totalHits;
    protected Instant recency;

    public ResultSetImpl() {
        this.results = new ArrayList<>();
    }

    public ResultSetImpl(int initialCapacity) {
        this.results = new ArrayList<>(initialCapacity);
    }

    @Override
    public PropertySet getResult(int index) {
        return this.results.get(index);
    }

    @Override
    public boolean hasResult(int index) {
        return (this.results.size() >= index + 1);
    }

    @Override
    public List<PropertySet> getResults(int maxIndex) {
        int max = Math.min(maxIndex, this.results.size());

        return this.results.subList(0, max);
    }

    @Override
    public List<PropertySet> getResults(int fromIndex, int toIndex) {
        return this.results.subList(fromIndex, toIndex);
    }

    @Override
    public List<PropertySet> getAllResults() {
        return this.results;
    }

    @Override
    public int getSize() {
        return this.results.size();
    }

    /**
     * Add a result to this result set.
     * @param propSet any instance of {@link PropertySet}.
     */
    public void addResult(PropertySet propSet) {
        this.results.add(propSet);
    }

    @Override
    public Iterator<PropertySet> iterator() {
        return this.results.iterator();
    }

    @Override
    public int getTotalHits() {
        return this.totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append(": [size=").append(this.results.size());
        sb.append(", totalHits=").append(this.totalHits).append("]");
        return sb.toString();
    }

    public void setRecency(Instant timestamp) {
        this.recency = timestamp;
    }

    @Override
    public Optional<Instant> recency() {
        return Optional.ofNullable(recency);
    }

}
