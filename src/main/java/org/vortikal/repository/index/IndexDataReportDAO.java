/* Copyright (c) 2009, University of Oslo, Norway
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
package org.vortikal.repository.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanFilter;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilterClause;
import org.codehaus.plexus.util.FastMap;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.index.mapping.DocumentMapper;
import org.vortikal.repository.index.mapping.FieldNameMapping;
import org.vortikal.repository.index.mapping.FieldValueMapper;
import org.vortikal.repository.reporting.DataReportException;
import org.vortikal.repository.reporting.Pair;
import org.vortikal.repository.reporting.PropertyValueFrequencyQuery;
import org.vortikal.repository.reporting.ValueFrequencyPairComparator;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.ConfigurablePropertySelect;
import org.vortikal.repository.search.query.filter.TermExistsFilter;
import org.vortikal.repository.store.DataReportDAO;

/**
 * Experimental data-report DAO using system index instead of database.
 * 
 * An advantage with this is that we get complete ACL filtering nearly for free.
 * The SqlMapDataReportDAO does not have any ACL-handling ..
 * 
 * It might also be the case that this is actually faster than going the
 * database-route, even for large repositories. But that's speculation, it needs
 * to be tested .. Hopefully, we can completely replace the database-DAO with
 * this one.
 * 
 */
public class IndexDataReportDAO implements DataReportDAO {

    private LuceneIndexManager systemIndexAccessor;
    private DocumentMapper documentMapper;
    private FieldValueMapper fieldValueMapper;
    private IndexDataReportScopeFilterFactory scopeFilterFactory;
    private int maxIndexDirtyAge = 0;

    @SuppressWarnings("unchecked")
    public List<Pair<Value, Integer>>
            executePropertyFrequencyValueQuery(PropertyValueFrequencyQuery query)
            throws DataReportException {

        IndexReader reader = null;
        try {
            reader = systemIndexAccessor.getReadOnlyIndexReader(this.maxIndexDirtyAge);

            final PropertyTypeDefinition def = query.getPropertyTypeDefintion();

            BooleanFilter mainFilter = new BooleanFilter();

            // Optimization: only consider resources that actually have the property in question
            Filter propertyExistsFilter = getPropertyExistsFilter(def);

            // Get general scope filter from factory
            Filter scopeFilter = this.scopeFilterFactory.getScopeFilter(query.getScoping(), reader);

            mainFilter.add(new FilterClause(propertyExistsFilter, BooleanClause.Occur.MUST));
            if (scopeFilter != null) {
                mainFilter.add(new FilterClause(scopeFilter, BooleanClause.Occur.MUST));
            }

            ConfigurablePropertySelect selector = new ConfigurablePropertySelect();
            selector.addPropertyDefinition(def);
            FieldSelector fieldSelector = this.documentMapper.getDocumentFieldSelector(selector);

            FastMap valFreqMap = new FastMap(1024);

            DocIdSet allowedDocs = mainFilter.getDocIdSet(reader);
            DocIdSetIterator iterator = allowedDocs.iterator();
            final String fieldName = FieldNameMapping.getStoredFieldName(def);
            while (iterator.next()) {
                Document doc = reader.document(iterator.doc(), fieldSelector);
                Field[] fields = doc.getFields(fieldName);

                if (fields.length == 0) { // This check should be unnecessary
                    // now, since we're always using term
                    // exists filter, but keep it for safety.
                    continue;
                }

                if (def.isMultiple()) {
                    Value[] values = this.fieldValueMapper.getValuesFromStoredBinaryFields(
                                                                          Arrays.asList(fields),
                                                                          def.getType());
                    for (Value value : values) {
                        addValue(valFreqMap, value);
                    }
                } else {
                    if (fields.length != 1) {
                        throw new DataReportException("Index error (multiple values for single valued property)");
                    }
                    addValue(valFreqMap, this.fieldValueMapper.getValueFromStoredBinaryField(fields[0], def.getType()));
                }
            }

            int minFreq = query.getMinValueFrequency();
            List<Pair<Value, Integer>> retval = new ArrayList<Pair<Value, Integer>>(valFreqMap.size());
            for (Object o : valFreqMap.entrySet()) {
                Map.Entry entry = (Map.Entry) o;

                Integer freq = (Integer) entry.getValue();
                Value value = (Value) entry.getKey();

                if (freq.intValue() >= minFreq) {
                    retval.add(new Pair<Value, Integer>(value, freq));
                }
            }

            // Sort
            if (query.getOrdering() != PropertyValueFrequencyQuery.Ordering.NONE) {
                Collections.sort(retval, new ValueFrequencyPairComparator(query.getOrdering()));
            }

            // Apply any limit to number of results
            if (query.getLimit() != PropertyValueFrequencyQuery.LIMIT_UNLIMITED) {
                int limit = Math.min(query.getLimit(), retval.size());
                retval = retval.subList(0, limit);
            }

            return retval;
        } catch (IOException io) {
            throw new DataReportException(io);
        } finally {
            if (reader != null) {
                try {
                    this.systemIndexAccessor.releaseReadOnlyIndexReader(reader);
                } catch (IOException io) {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addValue(Map valFreqMap, Value value) {
        Integer currentFreq = (Integer) valFreqMap.get(value);
        if (currentFreq == null) {
            currentFreq = new Integer(1);
        } else {
            currentFreq = new Integer(currentFreq.intValue() + 1);
        }
        valFreqMap.put(value, currentFreq);
    }

    private Filter getPropertyExistsFilter(PropertyTypeDefinition def) {
        String fieldName = FieldNameMapping.getSearchFieldName(def, false);
        return new TermExistsFilter(fieldName);
    }

    @Required
    public void setSystemIndexAccessor(LuceneIndexManager systemIndexAccessor) {
        this.systemIndexAccessor = systemIndexAccessor;
    }

    @Required
    public void setDocumentMapper(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Required
    public void setFieldValueMapper(FieldValueMapper fieldValueMapper) {
        this.fieldValueMapper = fieldValueMapper;
    }

    /**
     * @param scopeFilterFactory the scopeFilterFactory to set
     */
    @Required
    public void setScopeFilterFactory(IndexDataReportScopeFilterFactory scopeFilterFactory) {
        this.scopeFilterFactory = scopeFilterFactory;
    }

    /**
     * @param maxIndexDirtyAge the maxIndexDirtyAge to set
     */
    public void setMaxIndexDirtyAge(int maxIndexDirtyAge) {
        this.maxIndexDirtyAge = maxIndexDirtyAge;
    }
}
