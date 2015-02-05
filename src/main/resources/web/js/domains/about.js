/*
 * About
 * 
 * Meta-data about a resource
 * 
 */
 
$.when(vrtxAdmin.domainsIsReady).done(function() {
  var vrtxAdm = vrtxAdmin, _$ = vrtxAdm._$;
  
  switch (vrtxAdm.bodyId) {
    case "vrtx-about":
      vrtxAdm.zebraTables(".resourceInfo");

      if (!vrtxAdmin.isIE7) { // Turn of tmp. in IE7
        var propsAbout = ["contentLocale", "commentsEnabled", "userTitle", "keywords", "description",
                        "verifiedDate", "authorName", "authorEmail", "authorURL", "collection-type",
                        "contentType", "userSpecifiedCharacterEncoding", "plaintext-edit", "xhtml10-type",
                        "obsoleted", "editorial-contacts"];
        for (i = propsAbout.length; i--;) {
          vrtxAdm.getFormAsync({
            selector: ".prop-" + propsAbout[i] + " a.vrtx-button-small",
            selectorClass: "expandedForm-prop-" + propsAbout[i],
            insertAfterOrReplaceClass: "tr.prop-" + propsAbout[i],
            nodeType: "tr",
            isReplacing: true,
            simultanSliding: true
          });
          vrtxAdm.completeFormAsync({
            selector: ".prop-" + propsAbout[i] + " form input[type=submit]",
            isReplacing: true
          });
        }
      }

      var takenOwnership = false;
      vrtxAdm.cachedDoc.on("submit", "#vrtx-admin-ownership-form", function (e) {
        if (!takenOwnership) {
          var d = new VrtxConfirmDialog({
            msg: confirmTakeOwnershipMsg,
            title: confirmTakeOwnershipTitle,
            onOk: function () {
              takenOwnership = true;
              _$("#vrtx-admin-ownership-form").submit();
            }
          });
          d.open();
          e.stopPropagation();
          e.preventDefault();
        } else {
          e.stopPropagation();
        }
      });

      // Urchin stats
      vrtxAdm.cachedBody.on("click", "#vrtx-resource-visit-tab-menu a", function (e) {
        if (vrtxAdm.asyncGetStatInProgress) {
          return false;
        }
        vrtxAdm.asyncGetStatInProgress = true;

        var link = _$(this);
        var liElm = link.parent();
        if (liElm.hasClass("first")) {
          liElm.removeClass("first").addClass("active active-first");
          liElm.next().removeClass("active active-last").addClass("last");
        } else {
          liElm.removeClass("last").addClass("active active-last");
          liElm.prev().removeClass("active active-first").addClass("first");
        }

        _$("#vrtx-resource-visit-wrapper").append("<span id='urchin-loading'></span>");
        _$("#vrtx-resource-visit-chart-stats-info").remove();
        vrtxAdm.serverFacade.getHtml(this.href, {
          success: function (results, status, resp) {
            _$("#urchin-loading").remove();
            _$("#vrtx-resource-visit").append("<div id='vrtx-resource-visit-chart-stats-info'>" + _$($.parseHTML(results)).find("#vrtx-resource-visit-chart-stats-info").html() + "</div>");
            vrtxAdm.asyncGetStatInProgress = false;
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
      break;
    default:
      break;
  }
});

/**
 * Generate zebra rows in table (PE)
 *
 * @this {VrtxAdmin}
 * @param {string} selector The table selector
 */
 VrtxAdmin.prototype.zebraTables = function zebraTables(selector) {
  var _$ = this._$;
  var table = _$("table" + selector);
  if (!table.length) return;
  if (this.isIE8) { // http://www.quirksmode.org/css/contents.html
    table.find("tbody tr:odd").addClass("even"); // hmm.. somehow even is odd and odd is even
    table.find("tbody tr:first-child").addClass("first");
  }
};