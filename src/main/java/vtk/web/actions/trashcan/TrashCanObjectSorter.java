/* Copyright (c) 2004, University of Oslo, Norway
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
package vtk.web.actions.trashcan;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import vtk.repository.RecoverableResource;
import vtk.repository.Resource;
import vtk.security.Principal;
import vtk.web.service.Service;
import vtk.web.service.URL;

public class TrashCanObjectSorter {

    public static final String SORT_BY_PARAM = "sort-by";
    public static final String INVERT_PARAM = "invert";

    public static enum Order {
        BY_NAME("name"), BY_DELETED_BY("deleted-by"), BY_DELETED_TIME("deleted-time");

        private String type;

        private Order(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

    }

    public static Map<String, TrashCanSortLink> getSortLinks(Service service, Resource resource, Principal principal,
            HttpServletRequest request) {
        Map<String, TrashCanSortLink> sortLinks = new HashMap<>();
        Order requestedSortOrder = TrashCanObjectSorter.getSortOrder(request
                .getParameter(TrashCanObjectSorter.SORT_BY_PARAM));
        for (Order order : Order.values()) {
            Map<String, List<String>> parameters = new HashMap<>();
            parameters.put(TrashCanObjectSorter.SORT_BY_PARAM, Collections.singletonList(order.getType()));
            boolean invert = request.getParameter(TrashCanObjectSorter.INVERT_PARAM) != null;
            boolean selected = false;
            if (order.equals(requestedSortOrder)) {
                selected = true;
                if (!invert) {
                    parameters.put(TrashCanObjectSorter.INVERT_PARAM, Collections.singletonList("true"));
                }
            }
            URL url = service.urlConstructor(URL.create(request))
                    .withResource(resource)
                    .withPrincipal(principal)
                    .withParameters(parameters)
                    .constructURL();
            
            sortLinks.put(order.getType(), new TrashCanSortLink(url.toString(), selected));
        }
        return sortLinks;
    }

    public static Order getSortOrder(String requestedSortOrder) {
        // Default, also returned if invalid/unknown requested sort order
        Order order = Order.BY_NAME;
        if ("deleted-by".equals(requestedSortOrder)) {
            order = Order.BY_DELETED_BY;
        } else if ("deleted-time".equals(requestedSortOrder)) {
            order = Order.BY_DELETED_TIME;
        }
        return order;
    }

    public static void sort(List<TrashCanObject> trashCanObjects, Order order, boolean inverted) {

        Comparator<TrashCanObject> comparator = null;

        switch (order) {
        case BY_NAME:
            comparator = new TrashCanObjectNameComparator(inverted);
            break;
        case BY_DELETED_BY:
            comparator = new TrashCanObjectDeletedByComparator(inverted);
            break;
        case BY_DELETED_TIME:
            comparator = new TrashCanObjectDeletedTimeComparator(inverted);
            break;
        default:
            comparator = new TrashCanObjectNameComparator(inverted);
            break;
        }

        Collections.sort(trashCanObjects, comparator);
    }

    private static class TrashCanObjectNameComparator implements Comparator<TrashCanObject> {
        private boolean invert = false;

        public TrashCanObjectNameComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(TrashCanObject tco1, TrashCanObject tco2) {
            String n1 = tco1.getRecoverableResource().getName();
            String n2 = tco2.getRecoverableResource().getName();
            if (n1.equals(n2)) {
                Date d1 = tco1.getRecoverableResource().getDeletedTime();
                Date d2 = tco2.getRecoverableResource().getDeletedTime();
                // most recently deleted first
                return d2.compareTo(d1);
            }
            if (!this.invert) {
                return n1.compareTo(n2);
            }
            return n2.compareTo(n1);
        }
    }

    private static class TrashCanObjectDeletedByComparator implements Comparator<TrashCanObject> {
        private boolean invert = false;

        public TrashCanObjectDeletedByComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(TrashCanObject tco1, TrashCanObject tco2) {
            String d1 = tco1.getRecoverableResource().getDeletedBy();
            String d2 = tco2.getRecoverableResource().getDeletedBy();
            if (d1.equals(d2)) {
                Date da1 = tco1.getRecoverableResource().getDeletedTime();
                Date da2 = tco2.getRecoverableResource().getDeletedTime();
                // most recently deleted first
                return da2.compareTo(da1);
            }
            if (!this.invert) {
                return d1.compareTo(d2);
            }
            return d2.compareTo(d1);
        }
    }

    private static class TrashCanObjectDeletedTimeComparator implements Comparator<TrashCanObject> {
        private boolean invert = false;

        public TrashCanObjectDeletedTimeComparator(boolean invert) {
            this.invert = invert;
        }

        @Override
        public int compare(TrashCanObject tco1, TrashCanObject tco2) {
            RecoverableResource r1 = tco1.getRecoverableResource();
            RecoverableResource r2 = tco2.getRecoverableResource();
            if (r1.getFormattedDeletedTime().equals(r2.getFormattedDeletedTime())) {
                // If deleted simultaneously (down to sec. precision) ->
                // sort on name
                String n1 = r1.getName();
                String n2 = r2.getName();
                return n1.compareTo(n2);
            }
            Date d1 = r1.getDeletedTime();
            Date d2 = r2.getDeletedTime();
            if (!this.invert) {
                return d2.compareTo(d1);
            }
            return d1.compareTo(d2);
        }
    }

}
