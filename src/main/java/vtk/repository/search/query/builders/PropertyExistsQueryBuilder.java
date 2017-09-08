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
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import vtk.repository.index.mapping.Fields;
import vtk.repository.index.mapping.PropertyFields;
import vtk.repository.resourcetype.PropertyType;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.repository.search.query.LuceneQueryBuilder;
import vtk.repository.search.query.PropertyExistsQuery;
import vtk.repository.search.query.QueryBuilder;
import vtk.repository.search.query.QueryBuilderException;


/**
 * 
 */
public class PropertyExistsQueryBuilder implements QueryBuilder {

    private final PropertyExistsQuery query;
    private final PropertyTypeDefinition def;
    private final PropertyFields pf;

    public PropertyExistsQueryBuilder(PropertyExistsQuery query, PropertyTypeDefinition def, PropertyFields pf) {
        this.query = query;
        this.def = def;
        this.pf = pf;
    }

    @Override
    public org.apache.lucene.search.Query buildQuery() throws QueryBuilderException {

        // Only field name matters for exists queries
        
        String fieldName = PropertyFields.propertyFieldName(def, false);
        if (def.getType() == Type.JSON && query.complexValueAttributeSpecifier().isPresent()) {
            fieldName = PropertyFields.jsonFieldName(def, query.complexValueAttributeSpecifier().get(), false);
        }

        Query q = new TermQuery(new Term(Fields.FIELD_NAMES_METAFIELD, fieldName));
        if (query.isInverted()) {
            q = LuceneQueryBuilder.invert(q);
        }

        return q;
    }
}
