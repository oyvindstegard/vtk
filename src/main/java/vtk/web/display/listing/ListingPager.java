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
package vtk.web.display.listing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;

import vtk.web.service.URL;

public class ListingPager {

    private static final int PAGING_WINDOW = 10;

    public static final String UPCOMING_PAGE_PARAM = "page";
    public static final String PREVIOUS_PAGE_PARAM = "p-page";
    public static final String PREV_BASE_OFFSET_PARAM = "p-offset";
    public static final String USER_DISPLAY_PAGE = "u-page";

    public static class Pagination {
        private List<ListingPagingLink> urls;
        private URL self = null, first = null, last = null;
        private Optional<URL> next = Optional.empty(), previous = Optional.empty();
        
        // Legacy method:
        public List<ListingPagingLink> pageThroughLinks() { return urls; }
        
        public URL self() { return self; }
        public URL first() { return first; }
        public URL last() { return last; }
        
        public Optional<URL> next() { return next; }
        public Optional<URL> previous() { return previous; }
        
        private Pagination(URL base, List<ListingPagingLink> urls) {
            this.urls = Collections.unmodifiableList(urls);
            self = new URL(base);

            List<ListingPagingLink> numbered = new ArrayList<>();
            for (ListingPagingLink url: urls) {
                
                if ("prev".equals(url.getTitle())) { 
                    previous = Optional.of(url.getUrl());
                }
                else if ("next".equals(url.getTitle())) { 
                    next = Optional.of(url.getUrl());
                }
                else {
                    try {
                        Integer.parseInt(url.getTitle());
                        numbered.add(url);
                    }
                    catch (NumberFormatException e) { }
                }
            }
            if (numbered.isEmpty()) {
                throw new IllegalStateException("No numbered pages in result");
            }
            first = new URL(numbered.get(0).getUrl());
            last = new URL(numbered.get(numbered.size() - 1).getUrl());
        }
        
        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + self + "," + first + "," 
                    + next + "," + previous + "," + last + ")";
        }
    }
    
    public static Pagination pagination(int hits, int pageLimit, URL baseURL, int currentPage) {
        List<ListingPagingLink> urls = generatePageThroughUrls(hits, pageLimit, baseURL, currentPage);
        return new Pagination(baseURL, urls);
    }
    
    public static Pagination pagination(int hits, int pageLimit, int hitsInFirstSearch,
            URL baseURL, boolean twoSearches, int currentPage) {
        List<ListingPagingLink> urls = generatePageThroughUrls(hits, pageLimit, hitsInFirstSearch, 
                baseURL, twoSearches, currentPage);
        return new Pagination(baseURL, urls);
    }
    
    
    
    private static List<ListingPagingLink> generatePageThroughUrls(int hits, int pageLimit, URL baseURL, int currentPage) {
        return generatePageThroughUrls(hits, pageLimit, 0, baseURL, false, currentPage);
    }

    private static List<ListingPagingLink> generatePageThroughUrls(int hits, int pageLimit, int hitsInFirstSearch,
            URL baseURL, boolean twoSearches, int currentPage) {
        if (pageLimit == 0) {
            return null;
        }
        List<ListingPagingLink> urls = new ArrayList<ListingPagingLink>();
        baseURL = new URL(baseURL).removeParameter(PREVIOUS_PAGE_PARAM).removeParameter(PREV_BASE_OFFSET_PARAM)
                .removeParameter(UPCOMING_PAGE_PARAM).removeParameter(USER_DISPLAY_PAGE).setCollection(true);

       
        int pages = hits / pageLimit;
        if ((hits % pageLimit) > 0) {
            pages += 1;
        }
        
        int pagesInFirstSearch = (hitsInFirstSearch / pageLimit);
        if ((hitsInFirstSearch % pageLimit) > 0) {
            pagesInFirstSearch += 1;
        }
        int offset = 0;
        if (hitsInFirstSearch > 0 && (hitsInFirstSearch % pageLimit) > 0) {
            offset = pageLimit - (hitsInFirstSearch % pageLimit);
        }

        int pageRange = PAGING_WINDOW;
        int range = pageRange / 2;
        int start = currentPage, stop = currentPage;

        /* Special case for page number 1 */
        if (pages <= pageRange || currentPage - range < 1) {

            /* If page one is current page then it needs to be marked. */
            urls.add(new ListingPagingLink("1", new URL(baseURL), currentPage == 1));

            /* Checks if page 1 is previous page for page 2. */
            if (currentPage == 2)
                urls.add(0, new ListingPagingLink("prev", new URL(baseURL), false));

            /* If page 1 is used the for loop needs to stop 1 loop earlier. */
            stop--;
        }

        /*
         * Rules for how the range works. First rule is if we do not need to do
         * anything. Second is for when we have enough room before current page.
         * Third is for when we have enough room after current page.
         */
        if (pages <= pageRange) {
            start = 1;
            stop = pages;
        } else if (start - range >= 1) {
            while (stop < pages && range != 0) {
                stop++;
                range--;
                pageRange--;
            }
            start -= pageRange;
        } else if (stop + range <= pages) {
            while (start > 1 && range != 0) {
                start--;
                range--;
                pageRange--;
            }
            stop += pageRange;
        }

        /*
         * ListingPagingLink next is set in the for-loop and then put at the end
         * of the list after the loop.
         */
        ListingPagingLink next = null;

        for (int i = start; i < stop; i++) {
            URL url = new URL(baseURL);
            if (hitsInFirstSearch == 0 && twoSearches) {
                url.setParameter(PREVIOUS_PAGE_PARAM, String.valueOf(i + 1));
            } else if (pagesInFirstSearch > i || hitsInFirstSearch == 0) {
                url.setParameter(UPCOMING_PAGE_PARAM, String.valueOf(i + 1));
            } else {
                if (pagesInFirstSearch > 1) {
                    url.setParameter(UPCOMING_PAGE_PARAM, String.valueOf(pagesInFirstSearch));
                }
                if (offset > 0) {
                    url.setParameter(PREV_BASE_OFFSET_PARAM, String.valueOf(offset));
                }
                url.setParameter(PREVIOUS_PAGE_PARAM, String.valueOf(start++));
            }
            url.setParameter(USER_DISPLAY_PAGE, String.valueOf(i + 1));

            /* Puts ListingPagingLink in the list. */
            urls.add(new ListingPagingLink((i + 1) + "", url, currentPage == (i + 1)));

            /* Puts previous at start of the list */
            if (currentPage != 1 && currentPage - 2 == i) {
                urls.add(0, new ListingPagingLink("prev", url, false));
            }

            /* Stores next to put in list later. */
            if (currentPage != pages && currentPage == i) {
                next = new ListingPagingLink("next", url, false);
            }
        }

        /* Puts next at the end of the list. */
        if (next != null)
            urls.add(next);

        return urls;
    }

    public static int getPage(HttpServletRequest request, String parameter) {
        int page = 1;
        String pageParam = request.getParameter(parameter);
        if (StringUtils.isNotBlank(pageParam)) {
            try {
                page = Integer.parseInt(pageParam);
                if (page < 1) {
                    page = 1;
                }
            } catch (Throwable t) {
            }
        }
        return page;
    }

    public static URL removePagerParms(URL url){
        url.removeParameter(PREVIOUS_PAGE_PARAM);
        url.removeParameter(PREV_BASE_OFFSET_PARAM);
        url.removeParameter(UPCOMING_PAGE_PARAM);
        url.removeParameter(USER_DISPLAY_PAGE);
        return url;
    }
    
}
