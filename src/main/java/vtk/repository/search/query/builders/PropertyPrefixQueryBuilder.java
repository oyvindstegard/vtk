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
package vtk.repository.search.query.builders;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.PropertyPrefixQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;
import vtk.repository.search.query.TermOperator;

/**
 * 
 */
public class PropertyPrefixQueryBuilder implements QueryBuilder {

    private final PropertyPrefixQuery ppq;
    private final PropertyTypeDefinition def;

    public PropertyPrefixQueryBuilder(PropertyPrefixQuery ppq, PropertyTypeDefinition def) {
        this.ppq = ppq;
        this.def = def;
    }

    @Override
    public Query buildQuery() throws QueryBuilderException {
        String termValue = this.ppq.getTerm();
        
        PropertyType.Type valueType = def.getType();
        TermOperator op = ppq.getOperator();

        boolean inverted = (op == TermOperator.NE || op == TermOperator.NE_IGNORECASE);
        boolean ignorecase = (op == TermOperator.NE_IGNORECASE || op == TermOperator.EQ_IGNORECASE);

        if (ignorecase) {
            termValue = termValue.toLowerCase();
        }

        String fieldName = PropertyFields.propertyFieldName(def, ignorecase);
        if (valueType == Type.JSON) {
            if (! ppq.complexValueAttributeSpecifier().isPresent()) {
                throw new QueryBuilderException("Wildcard query on JSON fields requires complex attribute specifier");
            }

            fieldName = PropertyFields.jsonFieldName(
                    def, ppq.complexValueAttributeSpecifier().get(), ignorecase);
            valueType = PropertyFields.jsonFieldDataType(def, ppq.complexValueAttributeSpecifier().get());
        }

        if (!(valueType == Type.PRINCIPAL ||
                valueType == Type.STRING ||
                valueType == Type.HTML ||
                valueType == Type.JSON)) {
            throw new QueryBuilderException("Prefix queries are only supported for "
                    + "property types PRINCIPAL, STRING, HTML and JSON w/attribute specifier."
                    + "Use range queries for dates and numbers.");
        }

        Query q = new PrefixQuery(new Term(fieldName, termValue));

        return inverted ? LuceneQueryBuilder.invert(q) : q;
    }

}
