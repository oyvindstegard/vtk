/**
 * Manually approved resources
 *
 */

var MANUALLY_APPROVE_INITIALIZED = $.Deferred();

(function() {

  var lastManuallyApprovedLocations = "",
      manuallyApprovedLocationsTextfield,
      aggregatedLocationsTextfield,
      showApprovedOnly = false,
      asyncGenPageTimer,
      manuallyApproveTemplates = [];

  $(window).on("load", function() {

    // Retrieve initial resources
    manuallyApprovedLocationsTextfield = $("#resource\\.manually-approve-from");
    aggregatedLocationsTextfield = $("#resource\\.aggregation");

    // Set initial locations / aggregated locations and generate menu
    if(manuallyApprovedLocationsTextfield.length) {
	  // Retrieve HTML templates
	  var manuallyApprovedTemplatesRetrieved = $.Deferred();
	  manuallyApproveTemplates = vrtxAdmin.templateEngineFacade.get("manually-approve",
	    ["menu", "table-start", "table-row", "table-end", "navigation-next", "navigation-prev"],
	  manuallyApprovedTemplatesRetrieved);
      var locations, aggregatedlocations;
      var value = manuallyApprovedLocationsTextfield.val();
      lastManuallyApprovedLocations = $.trim(value);
      locations = lastManuallyApprovedLocations.split(",");
      if(aggregatedLocationsTextfield.length) {
        aggregatedlocations = $.trim(aggregatedLocationsTextfield.val());
        aggregatedlocations = aggregatedlocations.split(",");
      }

      $.when(manuallyApprovedTemplatesRetrieved).done(function() {
        retrieveResources(".", locations, aggregatedlocations, true);
        var html = vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["menu"], { approveShowAll: approveShowAll,
                                                                                             approveShowApprovedOnly: approveShowApprovedOnly });
        $($.parseHTML(html)).insertAfter("#manually-approve-container-title");
      });
    } else {
      MANUALLY_APPROVE_INITIALIZED.resolve();
    }
  });

  $(document).ready(function() {
    var manuallyApproveContainer = $("#manually-approve-container");

    vrtxAdmin.cachedAppContent.on("click", "#vrtx-manually-approve-tab-menu a", function(e) {
      var elem = $(this);
      var parent = elem.parent();
      elem.replaceWith("<span>" + elem.html() + "</span>"); // todo: use wrap()
      if(parent.hasClass("last")) {
        showApprovedOnly = true;
        parent.attr("class", "active active-last");
        var parentPrev = parent.prev();
        parentPrev.attr("class", "first");
        var parentPrevSpan = parentPrev.find("span");
        parentPrevSpan.replaceWith('<a href="javascript:void(0);">' + parentPrevSpan.html() + "</a>");
      } else {
        showApprovedOnly = false;
        parent.attr("class", "active active-first");
        var parentNext = parent.next();
        parentNext.attr("class", "last");
        var parentNextSpan = parentNext.find("span");
        parentNextSpan.replaceWith('<a href="javascript:void(0);">' + parentNextSpan.html() + "</a>");
      }
      $("#manually-approve-refresh").trigger("click");
      e.preventDefault();
    });

    vrtxAdmin.cachedAppContent.on("click", "#manually-approve-refresh", function(e) {
      clearTimeout(asyncGenPageTimer);
      $("#approve-spinner").remove();

      if(manuallyApprovedLocationsTextfield && manuallyApprovedLocationsTextfield.length) {
        var locations, aggregatedlocations;

        saveMultipleInputFields();

        var value = manuallyApprovedLocationsTextfield.val();
        lastManuallyApprovedLocations = $.trim(value);
        locations = lastManuallyApprovedLocations.split(",");
        if(aggregatedLocationsTextfield.length) {
          aggregatedlocations = $.trim(aggregatedLocationsTextfield.val());
          aggregatedlocations = aggregatedlocations.split(",");
        }

        retrieveResources(".", locations, aggregatedlocations, false);
      }
      e.stopPropagation();
      e.preventDefault();
    });

    // Add / remove manually approved uri's
    manuallyApproveContainer.on("change", "td.checkbox input", function(e) {
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
    manuallyApproveContainer.on("click", ".next", function(e) {
      var that = $(this).parent().parent();
      var next = that.next();
      if (next.attr("id") && next.attr("id").indexOf("approve-page") != -1) {
        that.addClass("approve-page-hidden");
        next.removeClass("approve-page-hidden");
      }
      return false;
    });

    // Paging - previous
    manuallyApproveContainer.on("click", ".prev", function(e) {
      var that = $(this).parent().parent();
      var prev = that.prev();
      if (prev.attr("id") && prev.attr("id").indexOf("approve-page") != -1) {
        that.addClass("approve-page-hidden");
        prev.removeClass("approve-page-hidden");
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
   * @param isInit as boolean
   */
  function retrieveResources(serviceUri, locations, aggregatedlocations, isInit) {

    if (showApprovedOnly) {
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
      $("#vrtx-manually-approve-tab-menu").filter(":visible").addClass("hidden");
      $("#manually-approve-container").filter(":visible").addClass("hidden");
      return;
    }

    var approvedTextfield = $("#resource\\.manually-approved-resources");
    if(!isInit) { // Clear approved resources textfield before repopulation
      approvedTextfield.val("");
    }

    // Add spinner
    $("#manually-approve-container-title").append("<span id='approve-spinner'>" + approveRetrievingData + "...</span>");

    vrtxAdmin.serverFacade.getJSON(getUri + "&no-cache=" + (+new Date()), {
      success: function (results, status, resp) {
        if (results != null && results.length > 0) {
          $("#vrtx-manually-approve-tab-menu").filter(":hidden").removeClass("hidden");
          $("#manually-approve-container").filter(":hidden").removeClass("hidden");
          generateManuallyApprovedContainer(results, isInit, approvedTextfield);
        } else {
          $("#approve-spinner").remove();
          if(!showApprovedOnly) {
            $("#vrtx-manually-approve-tab-menu").filter(":visible").addClass("hidden");
          } else {
            $("<p id='vrtx-manually-approve-no-approved-msg'>" + approveNoApprovedMsg + "</p>")
              .insertAfter("#vrtx-manually-approve-tab-menu");
          }
          $("#manually-approve-container").filter(":visible").addClass("hidden");
          if(isInit) {
            MANUALLY_APPROVE_INITIALIZED.resolve();
          }
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
   * @param resources as array
   * @param isInit as boolean
   * @param approvedTextfield as jQElm
   *
   */
  function generateManuallyApprovedContainer(resources, isInit, approvedTextfield) {

    // Initial setup
    var pages = 1,
        prPage = 15,
        len = resources.length,
        remainder = len % prPage,
        moreThanOnePage = len > prPage,
        totalPages = len > prPage ? (parseInt(len / prPage) + 1) : 1,
        enhanceTableRowsFunc = enhanceTableRows,
        generateTableRowFunc = generateTableRow,
        generateTableEndAndPageInfoFunc = generateTableEndAndPageInfo,
        generateNavAndEndPageFunc = generateNavAndEndPage,
        generateStartPageAndTableHeadFunc = generateStartPageAndTableHead,
        i = 0,
        html = generateStartPageAndTableHead(pages);

    var manuallyApproveContainer = $("#manually-approve-container");

    // If more than one page
    if (moreThanOnePage) {
      for (; i < prPage; i++) { // Generate first page synchronous
        html += generateTableRowFunc(resources[i], isInit, approvedTextfield);
      }
      html += generateTableEndAndPageInfoFunc(pages, prPage, len, false);
      pages++;
      html += generateNavAndEndPageFunc(i, html, prPage, remainder, pages, totalPages);
      manuallyApproveContainer.html(html);
      var manuallyApproveContainerTable = manuallyApproveContainer.find("table");
      enhanceTableRowsFunc(manuallyApproveContainerTable);
      html = generateStartPageAndTableHeadFunc(pages);
    } else {
      manuallyApproveContainer.html(""); // clear if only one page
    }

    // Update spinner with page generation progress
    $("#approve-spinner").html(approveGeneratingPage + " <span id='approve-spinner-generated-pages'>" + pages + "</span> " + approveOf + " " + totalPages + "...");

    // Generate rest of pages asynchronous
    asyncGenPageTimer = setTimeout(function() {
      html += generateTableRowFunc(resources[i], isInit, approvedTextfield);
      if ((i + 1) % prPage == 0) {
        html += generateTableEndAndPageInfoFunc(pages, prPage, len, false);
        pages++;
        if (i < len - 1) {
          html += generateNavAndEndPageFunc(i, html, prPage, remainder, pages, totalPages);
          manuallyApproveContainer.append(html);
          var manuallyApprovePage = manuallyApproveContainer.find("#approve-page-" + (pages - 1));
          var table = manuallyApproveContainer.find("table");
          enhanceTableRowsFunc(table);
          if (moreThanOnePage) {
            manuallyApprovePage.addClass("approve-page-hidden");
          }
          html = generateStartPageAndTableHeadFunc(pages);
        }
        $("#approve-spinner-generated-pages").html(pages);
      }
      i++;
      if (i < len) {
        asyncGenPageTimer = setTimeout(arguments.callee, 1);
      } else {
        if (remainder != 0) {
          html += generateTableEndAndPageInfoFunc(pages, prPage, len, true);
        } else {
          pages--;
        }
        if (len > prPage) {
          html += "<div class='prev-next'><a href='#page-" + (pages - 1) + "' class='prev' id='page-" + (pages - 1) + "'>"
                + approvePrev + " " + prPage + "</a></div>";
        }
        html += "</div>";
        manuallyApproveContainer.append(html);
        var table = manuallyApproveContainer.find("#approve-page-" + pages + " table");
        enhanceTableRowsFunc(table);
        manuallyApproveContainer.on("click", "th.checkbox input", function() {
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
          manuallyApproveContainer.find("#approve-page-" + pages).addClass("approve-page-hidden");
        }
        if(isInit) { // TODO (or feature): user will get unsaved msg until all pages with checkboxes is loaded async (difficult to avoid without running some code twice)
          MANUALLY_APPROVE_INITIALIZED.resolve();
        }
      }
    }, 1);

  }

  function enhanceTableRows(table) {
    table.find("input").removeAttr("disabled");
  }

  /* HTML generation functions */

  function generateStartPageAndTableHead(pages) {
    return vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["table-start"], { pages: pages,
                                                                                            approveTableTitle: approveTableTitle,
                                                                                            approveTableSrc: approveTableSrc,
                                                                                            approveTablePublished: approveTablePublished });
  }

  function generateTableRow(resource, isInit, approvedTextfield) {
    if(resource.approved && !isInit) { // Repopulate approved resources textfield
      var approvedTextfieldVal = approvedTextfield.val();
      if (approvedTextfieldVal.length) {
        approvedTextfield.val(approvedTextfieldVal + ", " + resource.uri);
      } else {
        approvedTextfield.val(resource.uri);
      }
    }
    return vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["table-row"], { resource: resource });
  }

  function generateTableEndAndPageInfo(pages, prPage, len, lastRow) {
    return vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["table-end"], { approveShowing: approveShowing,
                                                                                          page: (((pages - 1) * prPage) + 1),
                                                                                          last: lastRow ? len : pages * prPage,
                                                                                          approveOf: approveOf,
                                                                                          len: len });
  }

  function generateNavAndEndPage(i, html, prPage, remainder, pages, totalPages) {
    var html = "<div class='prev-next'>";
    if (i > prPage) {
      var prevPage = pages - 2;
      html += vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["navigation-prev"], { prevPage: prevPage,
                                                                                                   approvePrev: approvePrev,
                                                                                                   prPage: prPage });
    }
    html += vrtxAdmin.templateEngineFacade.render(manuallyApproveTemplates["navigation-next"], { pages: pages,
                                                                                                 approveNext: approveNext,
                                                                                                 nextPrPage: (pages < totalPages || remainder == 0) ? prPage : remainder });
    html += "</div></div>";
    return html;
  }

})();

/* ^ Manually approved resources */
