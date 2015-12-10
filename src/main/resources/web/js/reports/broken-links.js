/*
 *  Broken links report
 *
 */

vrtxAdmin._$(document).ready(function() {
   var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;

   vrtxAdm.cachedAppContent.on("click", "td.vrtx-report-broken-links-web-page a", function(e) {
     var openedWebpageWithBrokenLinks = openRegular(this.href, 1020, 800, "DisplayWebpageBrokenLinks");
     e.stopPropagation();
     e.preventDefault();
   });
   
   var brokenLinksFilters = _$("#vrtx-report-filters");
   if(brokenLinksFilters.length) {
     brokenLinksFilters.append("<a href='#' id='vrtx-report-filters-show-hide-advanced' onclick='javascript:void(0);'>" + filtersAdvancedShow + "...</a>");
     
     var html = "<div id='vrtx-report-filters-folders-include-exclude' class='solidExpandedForm'>"
                + "<h3>" + filtersAdvancedTitle + "</h3>"
                + "<div id='vrtx-report-filters-folders-exclude' class='report-filters-folders-exclude'><h4>" + filtersAdvancedExcludeTitle + "</h4>"
                + "<input class='vrtx-textfield' type='text' id='exclude-folders' size='25' /></div>"
                + "<div id='vrtx-report-filters-folders-include' class='report-filters-folders-include'><h4>" + filtersAdvancedIncludeTitle + "</h4>"
                + "<input class='vrtx-textfield' type='text' id='include-folders' size='25' /></div>"
                + "<a class='vrtx-button'>" + filtersAdvancedUpdate + "</a>"
             + "</div>";
     _$(html).insertAfter(brokenLinksFilters);
     
     initMultipleInputFields();

     _$.when(vrtxEditor.multipleFieldsBoxesDeferred).done(function() {
       if(_$(".report-filters-folders-exclude").length) {
         enhanceMultipleInputFields("report-filters-folders-exclude", false, true, 999, null, false);
       }   
       if(_$(".report-filters-folders-include").length) {     
         enhanceMultipleInputFields("report-filters-folders-include", false, true, 999, null, false);
       } 
     });

     var pairs = window.location.search.split(/\&/),
         includedFolders = "", excludedFolders = "", query = "", pair = "";
     for(var i = 0, pairsLen = pairs.length; i < pairsLen; i++) {
       if(pairs[i].match(/^include-path/g)) { // Add include folders
         pair = decodeURIComponent(pairs[i]);
         includedFolders += pair.split("=")[1] + ", ";
         query += "&" + pair;
       }
       if(pairs[i].match(/^exclude-path/g)) { // Add exclude folders
         pair = decodeURIComponent(pairs[i]);
         excludedFolders += pair.split("=")[1] + ", ";
         query += "&" + pair;
       }
     }

     var filterLinks = _$("#vrtx-report-filters ul a");
     for(var i = filterLinks.length;i--;) {
       filterLinks[i].href = filterLinks[i].href + query;
     }
    
    // If any included or excluded folders show advanced settings
    if(includedFolders.length || excludedFolders.length) { 
      _$("#vrtx-report-filters-folders-include-exclude").slideToggle(0);
      _$("#vrtx-report-filters-show-hide-advanced").text(filtersAdvancedHide + "...");
    }
    
    _$("#include-folders").val(includedFolders.substring(0, includedFolders.lastIndexOf(",")));
    _$("#exclude-folders").val(excludedFolders.substring(0, excludedFolders.lastIndexOf(",")));
    
    eventListen(vrtxAdm.cachedAppContent, "click", "#vrtx-report-filters #vrtx-report-filters-show-hide-advanced", function(ref) { // Show / hide advanced settings
      var container = _$("#vrtx-report-filters-folders-include-exclude");
      var brokenLinksAnimation = new VrtxAnimation({
        elem: container,
        afterIn: function() {
          _$("#vrtx-report-filters-show-hide-advanced").text(filtersAdvancedHide + "...");
        },
        afterOut: function() {
          _$("#vrtx-report-filters-show-hide-advanced").text(filtersAdvancedShow + "...");
        }
      });
      if(container.css("display") != "none") {
        brokenLinksAnimation.bottomUp();
      } else {
        brokenLinksAnimation.topDown();
      }
    });
    eventListen(vrtxAdm.cachedAppContent, "click", "#vrtx-report-filters-folders-include-exclude a.vrtx-button", function(ref) { // Filter exclude and include folders
      saveMultipleInputFields(); // Multiple to comma-separated
      // Build query string
      var includeFolders = unique(_$("#include-folders").val().split(",")); // Get included folders and remove duplicates
      var excludeFolders = unique(_$("#exclude-folders").val().split(",")); // Get excluded folders and remove duplicates
      var includeFoldersLen = includeFolders.length, excludeFoldersLen = excludeFolders.length,
          includeQueryString = "", excludeQueryString = ""; 
      for(var i = 0; i < includeFoldersLen; i++) {
        var theIncludeFolder = $.trim(includeFolders[i]);
        if(theIncludeFolder.length) {
          includeQueryString += "&include-path=" + encodeURIComponent(theIncludeFolder.replace(/\/$/, ''));
        }     
      }
      for(i = 0; i < excludeFoldersLen; i++) {
        var theExcludeFolder = $.trim(excludeFolders[i]);
        if(theExcludeFolder.length) {
          excludeQueryString += "&exclude-path=" + encodeURIComponent(theExcludeFolder.replace(/\/$/, ''));
        }
      }
      // Update URL in address bar
      var thehref = window.location.href;
      var indexOfIncludeFolder = thehref.indexOf("&include-path"),
          indexOfExcludeFolder = thehref.indexOf("&exclude-path"),
          indexOfIncludeORExcludeFolder = (indexOfIncludeFolder !== -1) ? (indexOfExcludeFolder !== -1) 
                                                                        ? Math.min(indexOfIncludeFolder, indexOfExcludeFolder)
                                                                        : indexOfIncludeFolder : indexOfExcludeFolder;
      if(indexOfIncludeORExcludeFolder !== -1) {
        window.location.href = thehref.substring(0, indexOfIncludeORExcludeFolder) + includeQueryString + excludeQueryString;
      } else {
        window.location.href = thehref + includeQueryString + excludeQueryString;      
      }
    });
  }
});

/* ^ Broken links report */