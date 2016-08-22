/* Copyright (c) 2010, University of Oslo, Norway
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
package vtk.web.decorating.components.menu;

import java.text.Collator;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import vtk.repository.PropertySet;
import vtk.repository.resourcetype.PropertyType.Type;
import vtk.repository.resourcetype.PropertyTypeDefinition;
import vtk.web.view.components.menu.MenuItem;

public class ListMenuComparator implements Comparator<MenuItem<PropertySet>> {

    private Collator collator;
    private PropertyTypeDefinition importancePropertyDef;
    private PropertyTypeDefinition navigationTitlePropDef;
    private PropertyTypeDefinition overrideSortProp;
    private boolean ascending = true;
    private boolean sortByName = false;

    public ListMenuComparator(Locale locale, PropertyTypeDefinition importancePropertyDef,
            PropertyTypeDefinition navigationTitlePropDef) {
        this.collator = Collator.getInstance(locale);
        this.importancePropertyDef = importancePropertyDef;
        this.navigationTitlePropDef = navigationTitlePropDef;
    }

    public ListMenuComparator(Locale locale, PropertyTypeDefinition importancePropertyDef,
            PropertyTypeDefinition navigationTitlePropDef, boolean ascending, boolean sortByName,
            PropertyTypeDefinition overrideSortProp) {
        this(locale, importancePropertyDef, navigationTitlePropDef);
        this.ascending = ascending;
        this.overrideSortProp = overrideSortProp;
        this.sortByName = sortByName;
    }

    public int compare(MenuItem<PropertySet> i1, MenuItem<PropertySet> i2) {
        if (sortByName) {
            if (ascending) {
                return collator.compare(i1.getValue().getName(), i2.getValue().getName());
            }
            return collator.compare(i2.getValue().getName(), i1.getValue().getName());
        }
        if (overrideSortProp != null) {
            Type t = i1.getValue().getProperty(overrideSortProp).getType();
            String overrideValue1 = null;
            String overrideValue2 = null;

            if (t.equals(Type.STRING)) {
                overrideValue1 = i1.getValue().getProperty(overrideSortProp).getStringValue();
                overrideValue2 = i2.getValue().getProperty(overrideSortProp).getStringValue();
                if (ascending) {
                    return collator.compare(overrideValue1, overrideValue2);
                }
                return collator.compare(overrideValue2, overrideValue1);
            } else if (t.equals(Type.TIMESTAMP) || t.equals(Type.DATE)) {
                Date d1 = i1.getValue().getProperty(overrideSortProp).getDateValue();
                Date d2 = i2.getValue().getProperty(overrideSortProp).getDateValue();
                if (ascending) {
                    return (int) (d2.getTime() - d1.getTime());
                }
                return (int) (d1.getTime() - d2.getTime());
            }

        }
        if (this.importancePropertyDef != null) {
            int importance1 = 0, importance2 = 0;
            if (i1.getValue().getProperty(this.importancePropertyDef) != null) {
                importance1 = i1.getValue().getProperty(this.importancePropertyDef).getIntValue();
            }
            if (i2.getValue().getProperty(this.importancePropertyDef) != null) {
                importance2 = i2.getValue().getProperty(this.importancePropertyDef).getIntValue();
            }
            if (importance1 != importance2) {
                return importance2 - importance1;
            }
        }
        String navigationTitleValue1 = null, navigationTitleValue2 = null;
        if (i1.getValue().getProperty(this.navigationTitlePropDef) != null) {
            navigationTitleValue1 = i1.getValue().getProperty(navigationTitlePropDef).getStringValue();
        }
        if (i2.getValue().getProperty(this.navigationTitlePropDef) != null) {
            navigationTitleValue2 = i2.getValue().getProperty(navigationTitlePropDef).getStringValue();
        }
        String value1, value2;
        if (navigationTitleValue1 != null) {
            value1 = navigationTitleValue1;
        } else {
            value1 = i1.getTitle();
        }
        if (navigationTitleValue2 != null) {
            value2 = navigationTitleValue2;
        } else {
            value2 = i2.getTitle();
        }
        if (ascending) {
            return collator.compare(value1, value2);
        }
        return collator.compare(value2, value1);
    }

	public void setAscending(boolean ascending) {
		this.ascending = ascending;
	}
}
