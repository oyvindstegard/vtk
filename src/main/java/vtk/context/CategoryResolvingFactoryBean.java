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
package vtk.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.config.AbstractFactoryBean;

/**
 * Factory bean looking up groups of objects based on the
 * {@link Categorizable} interface.
 */
public class CategoryResolvingFactoryBean<T> 
    extends AbstractFactoryBean<Collection<T>> 
    implements FactoryBean<Collection<T>> {

    private String category;
    private Comparator<T> comparator;

    @Required
    public void setCategory(String category) {
        this.category = category;
    }
    
    @Override
    public Collection<T> createInstance() throws Exception {
        Map<String, Categorizable> matchingBeans = 
                BeanFactoryUtils.beansOfTypeIncludingAncestors(
                        (ListableBeanFactory) getBeanFactory(), 
                        Categorizable.class, true, false);
        
        List<T> result = new ArrayList<T>();
        
        for (String id: matchingBeans.keySet()) {
            Categorizable cat = matchingBeans.get(id);
            Set<?> set = cat.getCategories();
            if (set != null && set.contains(this.category)) {
                @SuppressWarnings("unchecked")
                T bean = (T) getBeanFactory().getBean(
                        BeanFactoryUtils.transformedBeanName(id));
                result.add(bean);
            }
        }
        if (comparator != null) {
            Collections.sort(result, comparator);
        }
        return result;
    }

    @Override
    public Class<?> getObjectType() {
        return Collection.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
        
    }
    
    public void setComparator(Comparator<T> comparator) {
        this.comparator = comparator;
    }
}
