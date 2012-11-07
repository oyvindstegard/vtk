/**
 * Manually approved resources
 *
 */

var LAST_MANUALLY_APPROVED_LOCATIONS = "",
    MANUALLY_APPROVED_LOCATIONS_TEXTFIELD,
    AGGREGATED_LOCATIONS_TEXTFIELD,
    APPROVED_ONLY = false,
    ASYNC_GEN_PAGE_TIMER,
    MANUALLY_APPROVE_TEMPLATES = [];

$(window).load(function() {

  // Retrieve initial resources
  MANUALLY_APPROVED_LOCATIONS_TEXTFIELD = $("#resource\\.manually-approve-from");
  AGGREGATED_LOCATIONS_TEXTFIELD = $("#resource\\.aggregation");
  
  // Retrieve HTML templates
  var manuallyApprovedTemplatesRetrieved = $.Deferred();
  MANUALLY_APPROVE_TEMPLATES = vrtxAdmin.retrieveHTMLTemplates("manually-approve",
                                                               ["menu", "table-start", "table-row", 
                                                                "table-end", "navigation-next", "navigation-prev"],
                                                                manuallyApprovedTemplatesRetrieved);
                                                                
  // Set initial locations / aggregated locations and generate menu
  if(MANUALLY_APPROVED_LOCATIONS_TEXTFIELD.length) {
    var locations, aggregatedlocations;
    var value = MANUALLY_APPROVED_LOCATIONS_TEXTFIELD.val();
    LAST_MANUALLY_APPROVED_LOCATIONS = $.trim(value);
    locations = LAST_MANUALLY_APPROVED_LOCATIONS.split(",");
    if(AGGREGATED_LOCATIONS_TEXTFIELD.length) {
      aggregatedlocations = $.trim(AGGREGATED_LOCATIONS_TEXTFIELD.val());
      aggregatedlocations = aggregatedlocations.split(",");
    }

    $.when(manuallyApprovedTemplatesRetrieved).done(function() {
      retrieveResources(".", locations, aggregatedlocations);
      var html = $.mustache(MANUALLY_APPROVE_TEMPLATES["menu"], { approveShowAll: approveShowAll, 
                                                                  approveShowApprovedOnly: approveShowApprovedOnly });  
    
      $(html).insertAfter("#manually-approve-container-title"); 
    });
  }
});

$(document).ready(function() {

    $("#app-content").on("click", "#vrtx-manually-approve-tab-menu a", function(e) {
      var elem = $(this);
      var parent = elem.parent();
      elem.replaceWith("<span>" + elem.html() + "</span>");
      if(parent.hasClass("last")) {
        APPROVED_ONLY = true;
        parent.attr("class", "active active-last");
        var parentPrev = parent.prev();
        parentPrev.attr("class", "first");
        var parentPrevSpan = parentPrev.find("span");
        parentPrevSpan.replaceWith('<a href="javascript:void(0);">' + parentPrevSpan.html() + "</a>");
      } else {
        APPROVED_ONLY = false;
        parent.attr("class", "active active-first");
        var parentNext = parent.next();
        parentNext.attr("class", "last");
        var parentNextSpan = parentNext.find("span");
        parentNextSpan.replaceWith('<a href="javascript:void(0);">' + parentNextSpan.html() + "</a>");     
      }
      $("#manually-approve-refresh").trigger("click");
      e.preventDefault();
    });

    $("#app-content").on("click", "#manually-approve-refresh", function(e) {
      clearTimeout(ASYNC_GEN_PAGE_TIMER);
      $("#approve-spinner").remove();
      
      if(MANUALLY_APPROVED_LOCATIONS_TEXTFIELD && MANUALLY_APPROVED_LOCATIONS_TEXTFIELD.length) {
        var locations, aggregatedlocations;
        
        saveMultipleInputFields();
      
        var value = MANUALLY_APPROVED_LOCATIONS_TEXTFIELD.val();
        LAST_MANUALLY_APPROVED_LOCATIONS = $.trim(value);
        locations = LAST_MANUALLY_APPROVED_LOCATIONS.split(",");
        if(AGGREGATED_LOCATIONS_TEXTFIELD.length) {
          aggregatedlocations = $.trim(AGGREGATED_LOCATIONS_TEXTFIELD.val());
          aggregatedlocations = aggregatedlocations.split(",");
        }

        retrieveResources(".", locations, aggregatedlocations);  
      }
      e.stopPropagation();
      e.preventDefault();
    });

    // Add / remove manually approved uri's
    $("#manually-approve-container").on("change", "td.checkbox input", function(e) {
      var textfield = $("#resource\\.manually-approved-resources");
      var value = textfield.val();
      var uri = $(this).val();
      if (this.checked) {
        if (value.length) {
          value += ", " + uri;
        } else {
          value = uri;
        }
      } else {
        if (value.indexOf(uri) == 0) {
          value = value.replace(uri, "");
        } else {
          value = value.replace(", " + uri, "");
        }
      }
      textfield.val(value);
    });
    
    // Paging - next
    $("#manually-approve-container").on("click", ".next", function(e) {
      var that = $(this).parent();
      var next = that.next();
      if (next.attr("id") && next.attr("id").indexOf("approve-page") != -1) {
        $(that).hide();
        $(next).show();
      }
      return false;
    });

    // Paging - previous
    $("#manually-approve-container").on("click", ".prev", function(e) {
      var that = $(this).parent();
      var prev = that.prev();
      if (prev.attr("id") && prev.attr("id").indexOf("approve-page") != -1) {
        $(that).hide();
        $(prev).show();
      }
      return false;
    });
});

/**
 * Retrieves resources as JSON array for locations to manually approve from
 * 
 * @param serviceUri as string
 * @param locations as array
 * @param aggregatedlocations as array
 */

function retrieveResources(serviceUri, locations, aggregatedlocations) {

  if (APPROVED_ONLY) {
    var getUri = serviceUri + "/?vrtx=admin&service=manually-approve-resources&approved-only";
  } else {
    var getUri = serviceUri + "/?vrtx=admin&service=manually-approve-resources";
  }
  
  if (locations) {
    for (var i = 0, len = locations.length; i < len; i++) {
      getUri += "&locations=" + $.trim(locations[i]);
    }
  }
  if (aggregatedlocations) {
    for (i = 0, len = aggregatedlocations.length; i < len; i++) {
      getUri += "&aggregate=" + $.trim(aggregatedlocations[i]);
    }
  }
  
  $("#vrtx-manually-approve-no-approved-msg").remove();
  
  if(!locations.length) {
    $("#vrtx-manually-approve-tab-menu:visible").addClass("hidden");
    $("#manually-approve-container:visible").addClass("hidden");
    return;
  }

  // Add spinner
  $("#manually-approve-container-title").append("<span id='approve-spinner'>" + approveRetrievingData + "...</span>");
  
  vrtxAdmin.serverFacade.getJSON(getUri + "&no-cache=" + (+new Date()), {
    success: function (results, status, resp) {
      if (results != null && results.length > 0) {
        $("#vrtx-manually-approve-tab-menu:hidden").removeClass("hidden");
        $("#manually-approve-container:hidden").removeClass("hidden");
        
        generateManuallyApprovedContainer(results);
        
        if (typeof UNSAVED_CHANGES_CONFIRMATION !== "undefined") {
          storeInitPropValues();
        }
      } else {
        $("#approve-spinner").remove();
        if(!APPROVED_ONLY) {
          $("#vrtx-manually-approve-tab-menu:visible").addClass("hidden");
        } else {
          $("<p id='vrtx-manually-approve-no-approved-msg'>" + approveNoApprovedMsg + "</p>")
            .insertAfter("#vrtx-manually-approve-tab-menu");
        }
        $("#manually-approve-container:visible").addClass("hidden");
      }
    }
  });

}

/**
 * Generate tables with resources
 * 
 * First page synchronous (if more than one page) Rest of pages asynchrounous
 * adding each to DOM when complete
 * 
 * @param resources as JSON array
 * 
 */

function generateManuallyApprovedContainer(resources) {

  // Initial setup
  var pages = 1,
      prPage = 15, 
      len = resources.length,
      remainder = len % prPage,
      moreThanOnePage = len > prPage,
      totalPages = len > prPage ? (parseInt(len / prPage) + 1) : 1,
      generateTableRowFunc = generateTableRow,
      generateTableEndAndPageInfoFunc = generateTableEndAndPageInfo,
      generateNavAndEndPageFunc = generateNavAndEndPage,
      generateStartPageAndTableHeadFunc = generateStartPageAndTableHead,
      i = 0,
      html = generateStartPageAndTableHead(pages);
  
  // If more than one page
  if (moreThanOnePage) {
    for (; i < prPage; i++) { // Generate first page synchronous
      html += generateTableRowFunc(resources[i]);
    }
    html += generateTableEndAndPageInfoFunc(pages, prPage, len, false);
    pages++;
    html += generateNavAndEndPageFunc(i, html, prPage, remainder, pages, totalPages);
    
    var manuallyApproveContainer = $("#manually-approve-container");
    manuallyApproveContainer.html(html);
    var manuallyApproveContainerTable = manuallyApproveContainer.find("table");
    // TODO: probably faster to find all trs and then filter pseudo-selectors
    manuallyApproveContainerTable.find("tr:first-child").addClass("first");
    manuallyApproveContainerTable.find("tr:last-child").addClass("last");
    manuallyApproveContainerTable.find("tr:nth-child(even)").addClass("even");
    manuallyApproveContainerTable.find("input").removeAttr("disabled");
    html = generateStartPageAndTableHeadFunc(pages);
  } else {
    $("#manually-approve-container").html(""); // clear if only one page
  }

  // Update spinner with page generation progress
  $("#approve-spinner").html(approveGeneratingPage + " <span id='approve-spinner-generated-pages'>" + pages + "</span> " + approveOf + " " + totalPages + "...");
 
  // Generate rest of pages asynchronous
  ASYNC_GEN_PAGE_TIMER = setTimeout(function() {
    html += generateTableRowFunc(resources[i]);
    if ((i + 1) % prPage == 0) {
      html += generateTableEndAndPageInfoFunc(pages, prPage, len, false);
      pages++;
      if (i < len - 1) {
        html += generateNavAndEndPageFunc(i, html, prPage, remainder, pages, totalPages);
        $("#manually-approve-container").append(html);
        var table = $("#approve-page-" + (pages - 1) + " table");
        table.find("tr:first-child").addClass("first");
        table.find("tr:last-child").addClass("last");
        table.find("tr:nth-child(even)").addClass("even");
        table.find("input").removeAttr("disabled");
        var manuallyApproveContainer = $("#manually-approve-container");
        if (moreThanOnePage) {
          $("#manually-approve-container #approve-page-" + (pages - 1)).hide();
        }
        html = generateStartPageAndTableHeadFunc(pages);
      }
      $("#approve-spinner-generated-pages").html(pages);
    }
    i++;
    if (i < len) {
      ASYNC_GEN_PAGE_TIMER = setTimeout(arguments.callee, 1);
    } else {
      if (remainder != 0) {
        html += generateTableEndAndPageInfoFunc(pages, prPage, len, true);
      } else {
        pages--;
      }
      if (len > prPage) {
        html += "<a href='#page-" + (pages - 1) + "' class='prev' id='page-" + (pages - 1) + "'>" 
              + approvePrev + " " + prPage + "</a>";
      }
      html += "</div>";
      $("#manually-approve-container").append(html);
      var table = $("#approve-page-" + pages + " table");
      table.find("tr:first-child").addClass("first");
      table.find("tr:last-child").addClass("last");
      table.find("tr:nth-child(even)").addClass("even");
      table.find("input").removeAttr("disabled");
      $("#manually-approve-container").on("click", "th.checkbox input", function() {
        var checkAll = this.checked; 
        var checkboxes = $("td.checkbox input:visible");
        for(var i = 0, len = checkboxes.length; i < len; i++) {
          var checkbox = checkboxes[i];
          var isChecked = checkbox.checked;
          if (!isChecked && checkAll) { 
            $(checkbox).attr('checked', true).trigger("change");
          }
          if (isChecked && !checkAll) {
            $(checkbox).attr('checked', false).trigger("change");
          }
        }
      }); 
      $("#approve-spinner").remove();
      if (len > prPage) {
        $("#manually-approve-container #approve-page-" + pages).hide();
      }
      // TODO !spageti && !run twice
     if (typeof UNSAVED_CHANGES_CONFIRMATION !== "undefined") {
       storeInitPropValues();
     }
    }
  }, 1);

}

/* HTML generation functions */

function generateStartPageAndTableHead(pages) {
  return $.mustache(MANUALLY_APPROVE_TEMPLATES["table-start"], { pages: pages,
                                                                 approveTableTitle: approveTableTitle,
                                                                 approveTableSrc: approveTableSrc,
                                                                 approveTablePublished: approveTablePublished }); 
}

function generateTableRow(resource) {
  return $.mustache(MANUALLY_APPROVE_TEMPLATES["table-row"], { resource: resource });  
}

function generateTableEndAndPageInfo(pages, prPage, len, lastRow) {
  return $.mustache(MANUALLY_APPROVE_TEMPLATES["table-end"], { approveShowing: approveShowing,
                                                               page: (((pages - 1) * prPage) + 1),
                                                               last: lastRow ? len : pages * prPage,
                                                               approveOf: approveOf,
                                                               len: len });
}

function generateNavAndEndPage(i, html, prPage, remainder, pages, totalPages) {
  var html = $.mustache(MANUALLY_APPROVE_TEMPLATES["navigation-next"], { pages: pages,
                                                                         approveNext: approveNext,
                                                                         nextPrPage: (pages < totalPages || remainder == 0) ? prPage : remainder });
  if (i > prPage) {
    var prevPage = pages - 2;
    html += $.mustache(MANUALLY_APPROVE_TEMPLATES["navigation-prev"], { prevPage: prevPage,
                                                                        approvePrev: approvePrev,
                                                                        prPage: prPage });
  }
  html += "</div>";
  return html;
}


/* ^ HTML generation functions */

/* ^ Manually approved resources */