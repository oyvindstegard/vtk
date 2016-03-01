/* 
 * Visualize broken links
 *
 */

function visualizeBrokenLinks(options) {
  if (!options) throw "Missing argument";
  if (!options.validationURL) throw "Missing 'validationURL' field in argument";
  if (!options.selection) throw "Missing 'selection' field in argument";

  var selection = options.selection;
  var validationURL = options.validationURL;
  var linkClass = options.linkClass || 'vrtx-link-check';
  var chunk = options.chunk || 10;
  var urls = [];
  var idx = 0;
  urls[idx] = [];
  var context = $(selection);
  
  var links = filterOutDecorationTmpFix(linkClass, context);

  for (var i = 0, linksLength = links.length; i < linksLength; i++) {
    if (urls[idx].length === chunk) {
      idx++;
    }
    if (!urls[idx]) {
      urls[idx] = [];
    }
    var list = urls[idx];
    list[list.length] = links[i].href;
  }
  var urlsLength = urls.length;
  if (urlsLength === 0) {
    if (options.completed) options.completed(0);
    return;
  }
  var reqs = 0;
  var brokenLinks = 0;
  $.each(urls, function (k, list) {
    var data = "";
    for (var j = 0, listLength = list.length; j < listLength; j++) {
      data += list[j] + "\n";
    }
    $.ajax({
      type: 'POST',
      url: validationURL,
      data: data,
      contentType: 'text/plain;charset=utf-8',
      dataType: 'json',
      context: context,
      success: function (results, status, resp) {
        brokenLinks += linkCheckResponse(results, $(this), options.responseLocalizer, linkClass);
      },
      complete: function (req, status) {
        reqs++;
        if (reqs === urlsLength && options.completed) {
          options.completed(reqs, brokenLinks);
        }
      }
    });
  });
}

function filterOutDecorationTmpFix(linkClass, context) {
  // Clone only content inside UiO decoration (if exists)
  var links = context.contents().find("#right-main, #total-main").clone();
  if(!links.length) {
    links = context.contents().find("body").clone();
  }
  // Remove components without user content
  links.find("table.vrtx-unit-listing, table.vrtx-person-listing, .vrtx-alphabetical-project-listing, .vrtx-alphabetical-master-listing, .vrtx-listing-filter-results, .vrtx-listing-filter-status, .vrtx-listing-completed-ongoing, #vrtx-events-nav, .vrtx-paging-feed-wrapper, .comments, .comments-header, .vrtx-subfolder-menu, .vrtx-tab-menu, .vrtx-breadcrumb-menu, #vrtx-tags, .vrtx-tags, .vrtx-tags-service, .vrtx-tag-cloud").remove();
  
  // Remove all non-user content inside resources listings
  links.find(".vrtx-resources, #vrtx-daily-events, .vrtx-master-table, .vrtx-masters, .vrtx-programs, .vrtx-programs-inactive, .vrtx-program-options, .vrtx-program-options-inactive, .vrtx-person-list-participants").find("*:not(.description) a").remove();
  links.find(".vrtx-image-listing-container *:not(.vrtx-image-description) a").remove();
  links.find(".vrtx-image-table *:not(.vrtx-table-description) a").remove();

  // Remove all non-user content inside feed- and event-components
  links.find(".vrtx-recent-comments *:not(.item-description) a, .vrtx-feed *:not(.item-description) a, .vrtx-event-component *:not(.vrtx-event-component-introduction) a").remove();
  
  // Filtered out rest of Vortex-links
  links = links.find("a." + linkClass).filter(":not(.vrtx-icon, .more, .vrtx-resource-open-webdav, .vrtx-message-listing-edit, .more-url, .channel, .all-comments, .all-messages, .feed-title, .vrtx-ical, .vrtx-ical-help, .vrtx-event-component-title, .vrtx-image, #vrtx-feed-link, .vrtx-title, .item-title, .comments-title)");

  return links;
}

function linkCheckResponse(results, context, localizer, linkClass) {
  var links = context.contents().find("a." + linkClass);
  var brokenLinks = 0;
  for (var j = 0, linksLength = links.length; j < linksLength; j++) {
    var href = links[j].href;
    for (var i = 0, resultsLength = results.length; i < resultsLength; i++) {
      if (results[i].status !== "OK") {
        if (href === results[i].link) {
          if (results[i].status === "NOT_FOUND") {
            var color = "red";
            brokenLinks++;
          } else {
            var color = "brown";
            if (results[i].status === "ERROR") {
              //brokenLinks++;
            }
          }
          var msg = (localizer) ? localizer(results[i].status) : results[i].status;
          $(links[j]).append(" - [" + msg + "]").css("color", color).removeClass(linkClass);
          break;
        }
      }
    }
  }
  return brokenLinks;
}

/* ^ Visualize broken links */
