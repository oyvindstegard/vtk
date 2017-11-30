/* Copyright (c) 2017, University of Oslo, Norway
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static vtk.repository.RepositoryFixture.newUserToken;
import vtk.repository.index.PropertySetIndex;
import vtk.repository.index.consistency.ConsistencyCheck;
import vtk.repository.index.update.IncrementalUpdater;
import vtk.repository.search.ResultSet;
import vtk.repository.search.Search;
import vtk.repository.search.Searcher;
import vtk.repository.store.IndexDao;
import vtk.web.RequestContext;
import vtk.web.search.SearchParser;

@RunWith(Enclosed.class)
public class SearchIntegrationTest extends RepositoryFixture {

    private static final Repository repository;
    private static final PropertySetIndex systemIndex;
    private static final JdbcTemplate jdbcTemplate;
    private static final IncrementalUpdater incrementalUpdater;

    private static final Logger logger = LoggerFactory.getLogger(SearchIntegrationTest.class.getName());

    static {
        repository = getRepository(new RepositoryArchive("/index", "index.jar"));
        systemIndex = (PropertySetIndex) applicationContext.getBean("systemIndex");
        jdbcTemplate = new JdbcTemplate((DataSource)applicationContext.getBean("repository.dataSource"));
        incrementalUpdater = (IncrementalUpdater)applicationContext.getBean("repository.index.incrementalUpdater");

        waitForIndexUpdates();
    }

    // Waits for index to become updated as of the call to this method
    private static void waitForIndexUpdates() {
        repository.search(null, new Search().setWaitForPendingUpdates(Instant.now().plus(1, ChronoUnit.SECONDS), Duration.ofSeconds(600)));
    }
    
    private static Set<Path> repositoryPaths(Path prefix) {
        return jdbcTemplate.queryForList("SELECT uri FROM vortex_resource WHERE uri = ? OR uri LIKE ?",
                                          prefix.toString(), prefix.isRoot() ? "/%" : prefix.toString() + "/%")
                .stream()
                .map(m -> Path.fromString(m.get("uri").toString()))
                .collect(Collectors.toSet());
    }
    private static Set<Path> repositoryPaths() {
        return repositoryPaths(Path.ROOT);
    }

    public static class RepositoryIndexTest {
        @Test
        public void verifyIndexedPaths() throws Exception {
            Set<Path> repoPaths = repositoryPaths();

            int indexCount = 0;
            for (Iterator<Path> it = systemIndex.orderedUriIterator(); it.hasNext();) {
                ++indexCount;
                assertTrue(repoPaths.contains(it.next()));
            }

            assertEquals("Number of indexed paths not equal to paths in repository", repoPaths.size(), indexCount);
        }

        @Test
        public void propertyIndexCountAllInstances() {
            int repoResourceCount = repositoryPaths().size();
            assertEquals("Method PropertyIndex#countAllInstances works as expected",
                    repoResourceCount, systemIndex.countAllInstances());
        }

        @Test
        public void propertyIndexCountAllInstances_afterDelete() throws Exception {
            repository.delete(newUserToken("root@localhost"), null, Path.fromString("/index/delete"), false);

            int repoResourceCount = repositoryPaths().size();

            waitForIndexUpdates();

            assertEquals("Method PropertyIndex#countAllInstances works as expected",
                    repoResourceCount, systemIndex.countAllInstances());
        }

        @Test
        public void verifyIndexConsistency() {
            waitForIndexUpdates();

            IndexDao indexDao = (IndexDao)applicationContext.getBean("repository.indexDao");

            boolean locked = false;
            try {
                if (!(locked = systemIndex.lock(5))) {
                    throw new RuntimeException("Failed to lock system index before consistency check");
                }
                ConsistencyCheck check = ConsistencyCheck.run(systemIndex, indexDao, new File(System.getProperty("java.io.tmpdir")));
                assertTrue("No consistency check errors present", check.getErrors().isEmpty());
            } catch (Exception e) {
                fail("Index consistency check failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (locked) {
                    systemIndex.unlock();
                }
            }
        }
    }

    /**
     * Test various repository queries.
     */
    public static class RepositorySearchTest {

        private final Searcher searcher = (Searcher)applicationContext.getBean("systemIndexSearcher");
        private final SearchParser parserFactory = (SearchParser)applicationContext.getBean("searchParser");
        private final SearchParser.ContextualParser parser;
        private final RequestContext requestContext;

        public RepositorySearchTest() {
            requestContext = mock(RequestContext.class);
            when(requestContext.getResourceURI()).thenReturn(Path.fromString("/index"));
            when(requestContext.getCurrentCollection()).thenReturn(Path.fromString("/index"));
            HttpServletRequest req = new MockHttpServletRequest("GET", "/index");
            RequestContext.setRequestContext(requestContext, req);
            parser = parserFactory.parser(req);
        }

        @Test
        public void uriPrefixQuery() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/a*"));

            ResultSet rs = repository.search(null, search);
            assertEquals("Unexpected number of hits for search", 2, rs.getTotalHits());

            assertEquals(Path.fromString("/index/a"), rs.getResult(0).getURI());
            assertEquals(Path.fromString("/index/a/article1.html"), rs.getResult(1).getURI());
        }

        @Test
        public void uriPrefixQuery_notIncludingSelf() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/a/*"));

            ResultSet rs = repository.search(null, search);
            assertEquals("Unexpected number of hits for search", 1, rs.getTotalHits());
            assertEquals("/index/a/article1.html", rs.getResult(0).getURI().toString());
        }

        @Test
        public void searchFilterFlags_default() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/unpublished/* AND type IN file"));

            ResultSet rs = repository.search(null, search);
            assertEquals(0, rs.getSize());
        }

        @Test
        public void searchFilterFlags_unpublished() {
            Search search = new Search().clearAllFilterFlags().addFilterFlag(Search.FilterFlag.UNPUBLISHED);
            search.setQuery(parser.parse("uri = /index/unpublished/* AND type IN file"));

            ResultSet rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/unpublished/unpublished-collection/index.html", rs.getResult(0).getURI().toString());
        }

        @Test
        public void searchFilterFlags_unpublishedCollection() {
            Search search = new Search().clearAllFilterFlags().addFilterFlag(Search.FilterFlag.UNPUBLISHED_COLLECTIONS);
            search.setQuery(parser.parse("uri = /index/unpublished/* AND type IN file"));

            ResultSet rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/unpublished/index.html", rs.getResult(0).getURI().toString());
        }

        @Test
        public void searchFilterFlags_none() {
            Search search = new Search().clearAllFilterFlags();
            search.setQuery(parser.parse("uri = /index/unpublished/* AND type IN file"));

            ResultSet rs = repository.search(null, search);
            Set<String> resultPaths = StreamSupport.stream(rs.spliterator(), false).map(p -> p.getURI().toString()).collect(Collectors.toSet());
            // Keep tests like these independent on sorting order
            assertEquals(2, resultPaths.size());
            assertTrue(resultPaths.contains("/index/unpublished/unpublished-collection/index.html"));
            assertTrue(resultPaths.contains("/index/unpublished/index.html"));
        }

        @Test
        public void totalHits() {
            Set<Path> repoPaths = repositoryPaths(Path.fromString("/index"));

            Search search = new Search().clearAllFilterFlags();
            search.setQuery(parser.parse("uri = /index*"));
            search.setLimit(1);

            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertEquals(1, rs.getSize());
            assertEquals(repoPaths.size(), rs.getTotalHits());
        }
        
        @Test
        public void limitZero() {
            Set<Path> repoPaths = repositoryPaths(Path.fromString("/index"));

            Search search = new Search().clearAllFilterFlags();
            search.setQuery(parser.parse("uri = /index*"));
            search.setLimit(0); // Only interested in counting total matches
            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertEquals(0, rs.getSize());
            assertEquals(repoPaths.size(), rs.getTotalHits());
        }

        @Test
        public void limitAndOffset() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/numeric-names/*"));
            search.setSorting(parser.parseSortString("name ASC"));

            search.setLimit(1);

            search.setCursor(0);
            ResultSet rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/numeric-names/0000.txt", rs.getResult(0).getURI().toString());

            search.setCursor(1);
            rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/numeric-names/0001.txt", rs.getResult(0).getURI().toString());

            search.setCursor(2);
            rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/numeric-names/0002.txt", rs.getResult(0).getURI().toString());

            search.setLimit(2);
            rs = repository.search(null, search);
            assertEquals(2, rs.getSize());
            assertEquals("/index/numeric-names/0002.txt", rs.getResult(0).getURI().toString());
            assertEquals("/index/numeric-names/0003.txt", rs.getResult(1).getURI().toString());

            search.setLimit(1000);
            search.setCursor(10000);
            rs = repository.search(null, search);
            assertEquals(0, rs.getSize());
        }

        @Test
        public void propertyTermQuery() {
            Search search = new Search();
            search.setQuery(parser.parse("title = Article\\ 1 AND uri = /index*"));
            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/a/article1.html", rs.getResult(0).getURI().toString());
        }

        @Test
        public void propertyWildcardQuery() {
            Search search = new Search();
            search.setQuery(parser.parse("collectionTitle = *mission* AND uri = /index*"));
            ResultSet rs = repository.search(null, search);
            assertEquals(1, rs.getSize());
            assertEquals("/index/permissions", rs.getResult(0).getURI().toString());
        }

        @Test
        public void propertyRangeQuery_lowerBound() {
            Search search = new Search().clearAllFilterFlags().setSorting(null);
            search.setQuery(parser.parse("contentLength >= 100"));
            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertTrue(rs.getSize() > 0);
            for (PropertySet ps: rs) {
                assertTrue(ps.getPropertyByPrefix(null, "contentLength").getLongValue() >= 100);
            }
        }

        @Test
        public void propertyRangeQuery_upperBound() {
            Search search = new Search().clearAllFilterFlags().setSorting(null);
            search.setQuery(parser.parse("contentLength < 100"));
            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertTrue(rs.getSize() > 0);
            for (PropertySet ps: rs) {
                assertTrue(ps.getPropertyByPrefix(null, "contentLength").getLongValue() < 100);
            }
        }

        @Test
        public void nameWildcardQuery() {
            Search search = new Search().clearAllFilterFlags().setSorting(null);
            search.setQuery(parser.parse("name = *.txt"));
            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            assertTrue(rs.getSize() > 0);
            for (PropertySet ps: rs) {
                assertTrue(ps.getName().endsWith(".txt"));
            }
        }

        @Test
        public void sortingDefault() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/sorting/*"));
            ResultSet rs = repository.search(null, search);
            assertEquals(6, rs.getSize());
            String[] expectOrder = {"1.txt", "a.txt", "A.txt", "ab.txt", "c.txt", "z.txt"};
            for (int i=0; i<expectOrder.length; i++) {
                assertEquals("/index/sorting/" + expectOrder[i], rs.getResult(i).getURI().toString());
            }
        }

        @Test
        public void sortingDefault_reversed() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/sorting/*"));
            search.setSorting(parser.parseSortString("uri DESC"));
            ResultSet rs = repository.search(null, search);
            assertEquals(6, rs.getSize());
            String[] expectOrder = {"z.txt", "c.txt", "ab.txt", "A.txt", "a.txt", "1.txt"};
            for (int i=0; i<expectOrder.length; i++) {
                assertEquals("/index/sorting/" + expectOrder[i], rs.getResult(i).getURI().toString());
            }
        }

        @Test
        public void sortingContentLength() {
            Search search = new Search();
            search.setQuery(parser.parse("uri = /index/sorting/*"));
            search.setSorting(parser.parseSortString("contentLength DESC"));
            ResultSet rs = repository.search(null, search);
            assertEquals(6, rs.getSize());
            String[] expectOrder = {"z.txt", "c.txt", "ab.txt", "a.txt", "A.txt", "1.txt"};
            for (int i=0; i<expectOrder.length; i++) {
                assertEquals("/index/sorting/" + expectOrder[i], rs.getResult(i).getURI().toString());
            }
        }

        @Test
        public void queryAclFiltering_open() {
            Search search = new Search().setQuery(parser.parse("uri = /index/permissions/*"));

            ResultSet rs = repository.search(null, search);
            Set<String> resultPaths = StreamSupport.stream(rs.spliterator(), false)
                    .map(p -> p.getURI().toString()).collect(Collectors.toSet());

            assertEquals("Unexpected number of hits for search", 2, resultPaths.size());
            assertTrue(resultPaths.contains("/index/permissions/open"));
            assertTrue(resultPaths.contains("/index/permissions/open/README.md"));
        }

        @Test
        public void queryAclFiltering_user() {
            Search search = new Search().setQuery(parser.parse("uri = /index/permissions/*"));

            ResultSet rs = repository.search(newUserToken("user@localhost"), search);
            Set<String> resultPaths = StreamSupport.stream(rs.spliterator(), false)
                    .map(p -> p.getURI().toString()).collect(Collectors.toSet());

            assertEquals("Unexpected number of hits for search", 4, resultPaths.size());

            assertTrue(resultPaths.contains("/index/permissions/open"));
            assertTrue(resultPaths.contains("/index/permissions/open/README.md"));
            assertTrue(resultPaths.contains("/index/permissions/user"));
            assertTrue(resultPaths.contains("/index/permissions/user/README.md"));
        }

        @Test
        public void queryAclFiltering_root() {
            Search search = new Search().setQuery(parser.parse("uri = /index/permissions/*"));

            ResultSet rs = repository.search(newUserToken("root@localhost"), search);
            Set<String> resultPaths = StreamSupport.stream(rs.spliterator(), false)
                    .map(p -> p.getURI().toString()).collect(Collectors.toSet());

            assertEquals("Unexpected number of hits for search", 6, resultPaths.size());

            assertTrue(resultPaths.contains("/index/permissions/root"));
            assertTrue(resultPaths.contains("/index/permissions/root/README.md"));
            assertTrue(resultPaths.contains("/index/permissions/open"));
            assertTrue(resultPaths.contains("/index/permissions/open/README.md"));
            assertTrue(resultPaths.contains("/index/permissions/user"));
            assertTrue(resultPaths.contains("/index/permissions/user/README.md"));
        }

        @Test
        public void iterateAll() {
            Set<Path> repoPaths = repositoryPaths();

            Search search = new Search().clearAllFilterFlags().setLimit(Integer.MAX_VALUE).setSorting(null);
            search.setQuery(parser.parse("uri = /* OR uri = /"));

            final AtomicInteger count = new AtomicInteger(0);
            searcher.iterateMatching(newUserToken("root@localhost"), search, ps -> {
                count.incrementAndGet();
                assertTrue(repoPaths.contains(ps.getURI()));
                return true;
            });
            assertEquals(repoPaths.size(), count.get());
        }

        @Test
        public void iterateSorted() {
            Search search = new Search().clearAllFilterFlags().setLimit(Integer.MAX_VALUE);
            search.setQuery(parser.parse("uri = /index/numeric-names/*"));

            class Ref {
                Path instance = null;
            }
            final Ref previous = new Ref();
            searcher.iterateMatching(newUserToken("root@localhost"), search, ps -> {
                if (previous.instance != null) {
                    assertTrue(previous.instance.compareTo(ps.getURI()) < 0);
                }
                previous.instance = ps.getURI();
                return true;
            });
        }

        @Test
        public void iterateWithDateCriterium_sortedOnNumericField() throws Exception {
            Search search = new Search().clearAllFilterFlags().setLimit(Integer.MAX_VALUE);
            search.setQuery(parser.parse("creationTime >= 2017-11-30 AND (uri = /* OR uri = /)"));
            search.setSorting(parser.parseSortString("creationTime DESC"));

            Date oldest = new SimpleDateFormat("yyyy-MM-dd").parse("2017-11-30");

            class Ref {
                PropertySet instance = null;
            }
            final Ref previousPropSet = new Ref();
            final AtomicBoolean haveResultsFlag = new AtomicBoolean(false);
            searcher.iterateMatching(newUserToken("root@localhost"), search, ps -> {
                Date currentCreationTime = ps.getPropertyByPrefix(null, "creationTime").getDateValue();
                assertTrue(oldest.before(currentCreationTime) || oldest.equals(currentCreationTime));

                if (previousPropSet.instance != null) {
                    Date prevCreationTime = previousPropSet.instance.getPropertyByPrefix(null, "creationTime").getDateValue();
                    assertTrue(prevCreationTime.compareTo(currentCreationTime) >= 0); // Descending sort with most recent dates first
                }
                previousPropSet.instance = ps;
                haveResultsFlag.set(true);
                return true;
            });

            assertTrue("No search results with creationTime >= 2017-11-30", haveResultsFlag.get());
        }
    }

}