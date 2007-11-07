/* Copyright (c) 2006, 2007, University of Oslo, Norway
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
package org.vortikal.repository.search.query;


import static org.vortikal.repository.search.query.TermOperator.EQ;
import static org.vortikal.repository.search.query.TermOperator.NE;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.springframework.beans.factory.annotation.Required;
import org.vortikal.repository.HierarchicalVocabulary;
import org.vortikal.repository.PropertySetImpl;
import org.vortikal.repository.ResourceTypeTree;
import org.vortikal.repository.Vocabulary;
import org.vortikal.repository.index.LuceneIndexManager;
import org.vortikal.repository.index.mapping.BinaryFieldValueMapper;
import org.vortikal.repository.index.mapping.FieldNameMapping;
import org.vortikal.repository.index.mapping.FieldValueMapper;
import org.vortikal.repository.resourcetype.PropertyTypeDefinition;
import org.vortikal.repository.resourcetype.Value;
import org.vortikal.repository.search.query.builders.HierarchicalTermQueryBuilder;
import org.vortikal.repository.search.query.builders.NamePrefixQueryBuilder;
import org.vortikal.repository.search.query.builders.NameRangeQueryBuilder;
import org.vortikal.repository.search.query.builders.NameTermQueryBuilder;
import org.vortikal.repository.search.query.builders.NameWildcardQueryBuilder;
import org.vortikal.repository.search.query.builders.PropertyExistsQueryBuilder;
import org.vortikal.repository.search.query.builders.PropertyPrefixQueryBuilder;
import org.vortikal.repository.search.query.builders.PropertyRangeQueryBuilder;
import org.vortikal.repository.search.query.builders.PropertyTermQueryBuilder;
import org.vortikal.repository.search.query.builders.PropertyWildcardQueryBuilder;
import org.vortikal.repository.search.query.builders.QueryTreeBuilder;
import org.vortikal.repository.search.query.builders.TypeTermQueryBuilder;
import org.vortikal.repository.search.query.builders.UriDepthQueryBuilder;
import org.vortikal.repository.search.query.builders.UriPrefixQueryBuilder;
import org.vortikal.repository.search.query.builders.UriTermQueryBuilder;
import org.vortikal.util.repository.URIUtil;

/**
 * Factory that helps in building different Lucene queries 
 * from our own query types.
 * 
 * @author oyviste
 */
public final class QueryBuilderFactoryImpl implements QueryBuilderFactory {

    Log logger = LogFactory.getLog(QueryBuilderFactoryImpl.class);
    
    private LuceneIndexManager indexAccessor;
    private ResourceTypeTree resourceTypeTree;
    
    public QueryBuilder getBuilder(Query query) throws QueryBuilderException {
        
        QueryBuilder builder = null;

        if (query instanceof AbstractMultipleQuery) {
            builder = new QueryTreeBuilder(this, (AbstractMultipleQuery)query);
        }

        else if (query instanceof AbstractPropertyQuery) {
            builder = getAbstractPropertyQueryBuilder(query);
        }
       
        else if (query instanceof UriTermQuery) {
            builder = new UriTermQueryBuilder((UriTermQuery)query);
        }
       
        else if (query instanceof UriPrefixQuery) {
            String uri = ((UriPrefixQuery)query).getUri();
            boolean inverted = ((UriPrefixQuery)query).isInverted();
            Term idTerm = getPropertySetIdTermFromIndex(uri);
            builder =  new UriPrefixQueryBuilder(uri, idTerm, inverted);
        }
        
        else if (query instanceof UriDepthQuery) {
            builder = new UriDepthQueryBuilder((UriDepthQuery)query);
        }

        else if (query instanceof NameTermQuery) {
            builder = new NameTermQueryBuilder((NameTermQuery)query);
        }

        else if (query instanceof NameRangeQuery) {
            builder = new NameRangeQueryBuilder((NameRangeQuery)query);
        }
       
        else if (query instanceof NamePrefixQuery) {
            builder = new NamePrefixQueryBuilder((NamePrefixQuery)query);
        }

        else if (query instanceof NameWildcardQuery) {
            builder = new NameWildcardQueryBuilder((NameWildcardQuery)query);
        }
       
        else if (query instanceof TypeTermQuery) {
            TypeTermQuery ttq = (TypeTermQuery)query;
            
            if (EQ == ttq.getOperator() || NE == ttq.getOperator()) {
                builder = new TypeTermQueryBuilder(ttq.getTerm(), ttq.getOperator());
            } else {
                builder = new HierarchicalTermQueryBuilder<String>(this.resourceTypeTree, 
                        ttq.getOperator(), FieldNameMapping.RESOURCETYPE_FIELD_NAME, ttq.getTerm());
            }
        }
       
        if (builder == null) {
            throw new QueryBuilderException("Unsupported query type: " 
                                            + query.getClass().getName());
        }
        
        return builder;
    }
    
    private QueryBuilder getAbstractPropertyQueryBuilder(Query query)
        throws QueryBuilderException {

        if (query instanceof PropertyTermQuery) {
            PropertyTermQuery ptq = (PropertyTermQuery) query;
            PropertyTypeDefinition propDef = ptq.getPropertyDefinition();

            if (ptq.getOperator() == TermOperator.IN || ptq.getOperator() == TermOperator.NI) {
                Vocabulary<Value> vocabulary = propDef.getVocabulary();
                if (vocabulary == null || !(vocabulary instanceof HierarchicalVocabulary)) {
                    throw new QueryBuilderException("Property type doesn't have a hierachical vocabulary: " + propDef);
                }
                HierarchicalVocabulary<Value> hv = (HierarchicalVocabulary<Value>) vocabulary;
                
                String fieldName = FieldNameMapping.getSearchFieldName(propDef);
                String fieldValue = FieldValueMapper.encodeIndexFieldValue(ptq.getTerm(), propDef.getType());
                return new HierarchicalTermQueryBuilder<Value>(hv , ptq.getOperator(), fieldName, new Value(fieldValue));
            } 
            
            return new PropertyTermQueryBuilder(ptq.getOperator(), ptq.getTerm(),
                    FieldNameMapping.getSearchFieldName(propDef),FieldValueMapper.encodeIndexFieldValue(ptq.getTerm(), propDef.getType()));
        }
        
        if (query instanceof PropertyPrefixQuery) {
            return new PropertyPrefixQueryBuilder((PropertyPrefixQuery)query);
        }
        
        if (query instanceof PropertyRangeQuery) {
            return new PropertyRangeQueryBuilder((PropertyRangeQuery)query);
        }
        
        if (query instanceof PropertyWildcardQuery) {
            return new PropertyWildcardQueryBuilder((PropertyWildcardQuery)query);
        }
        
        if (query instanceof PropertyExistsQuery) {
            return new PropertyExistsQueryBuilder((PropertyExistsQuery)query);
        }
        
            throw new QueryBuilderException("Unsupported property query type: " 
                                        + query.getClass().getName());
    }
    
    private Term getPropertySetIdTermFromIndex(String uri) 
        throws QueryBuilderException {
        
        TermDocs td = null;
        IndexReader reader = null;
        try {
            reader = this.indexAccessor.getReadOnlyIndexReader();

            td = reader.termDocs(new Term(FieldNameMapping.URI_FIELD_NAME, 
                                                URIUtil.stripTrailingSlash(uri)));
            
            if (td.next()) {
                Field field= reader.document(td.doc()).getField(
                                            FieldNameMapping.STORED_ID_FIELD_NAME);
                
                String value = 
                    Integer.toString(
                            BinaryFieldValueMapper.getIntegerFromStoredBinaryField(field));
                
                return new Term(FieldNameMapping.ID_FIELD_NAME, value);
                
            }
            // URI not found, so the query should produce zero hits.
            return new Term(FieldNameMapping.ID_FIELD_NAME, String.valueOf(
                    PropertySetImpl.NULL_RESOURCE_ID));
        } catch (IOException io) {
            throw new QueryBuilderException("IOException while building query: " + io.getMessage());
        } finally {
            try {
                if (td != null) td.close();
                this.indexAccessor.releaseReadOnlyIndexReader(reader);
            } catch (IOException io) {}
        }
    }

    
    @Required public void setIndexAccessor(LuceneIndexManager indexAccessor) {
        this.indexAccessor = indexAccessor;
    }

    @Required public void setResourceTypeTree(ResourceTypeTree resourceTypeTree) {
        this.resourceTypeTree = resourceTypeTree;
    }

}
