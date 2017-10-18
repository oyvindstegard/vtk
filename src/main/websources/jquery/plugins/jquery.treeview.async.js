/*
 * Async Treeview 0.1 - Lazy-loading extension for Treeview
 *
 * http://bassistance.de/jquery-plugins/jquery-plugin-treeview/
 *
 * Copyright (c) 2007 JÃ¶rn Zaefferer
 *
 * Dual licensed under the MIT and GPL licenses:
 *   http://www.opensource.org/licenses/mit-license.php
 *   http://www.gnu.org/licenses/gpl.html
 *
 * Revision: $Id$
 *
 * USIT added JSON: 1. possible to set classes also on <li> (in addition to <span>)
 *                  2. uri in <a>
 *                  3. title in <a>
 *                  4. update settings.url on toggle()
 *                  5. service
 *
 * USIT * removed commented out method and set indent to 2 spaces
 *      * added error debug to AJAX function
 *      * callback on data load:
 *        http://stackoverflow.com/questions/4905101/how-to-add-jquery-treeview-callback-on-data-load
 *      * replace single quotes with HTML-entity
 *
 */

;
(function ($) {

  function load(settings, root, child, container) {
    function createNode(parent) {
      var linkOrPlainText = "";
      var theuri = this.uri;
      var text = vrtxAdmin.escapeHtml(this.text);
      if (theuri) {
        theuri = vrtxAdmin.escapeHtml(theuri);
        var title = this.title;
        if (title) {
          linkOrPlainText = "<a class='tree-link' href='" + theuri + "' title='" + title + "'>" + text + "</a>"
        } else {
          linkOrPlainText = "<a href='" + theuri + "'>" + text + "</a>"
        }
      } else {
        linkOrPlainText = text;
      }
      var current = $("<li/>").attr("id", this.id || "")
                              .html("<span>" + linkOrPlainText + "</span>")
                              .appendTo(parent);

      if (this.listClasses) current.addClass(this.listClasses);
      if (this.spanClasses) current.children("span").addClass(this.spanClasses);
      if (this.expanded) current.addClass("open");

      if (this.hasChildren || this.children && this.children.length) {
        var branch = $("<ul/>").appendTo(current);
        if (this.hasChildren) {
          current.addClass("hasChildren");
          createNode.call({
            classes: "placeholder",
            text: " ",
            children: []
          }, branch);
        }
        if (this.children && this.children.length) {
          $.each(this.children, createNode, [branch])
        }
      }
    }
    $.ajax($.extend(true, {
      url: settings.url,
      dataType: "json",
      data: {
        root: root
      },
      success: function (response) {
        if (settings.dataLoaded) {
          settings.dataLoaded();
        }
        child.empty();
        $.each(response, createNode, [child]);
        $(container).treeview({
          add: child
        });
      },
      error: function (jqXHR, textStatus, errorThrown) {
        if (typeof console !== "undefined" && console.log) {
          console.log(textStatus);
          if (console.dir) {
            console.dir(jqXHR);
          }
        }
      }
    }, settings.ajax));
  }

  var proxied = $.fn.treeview;
  $.fn.treeview = function (settings) {
    if (!settings.url) {
      return proxied.apply(this, arguments);
    }
    var container = this;
    if (!container.children().length) load(settings, "source", this, container);
    var userToggle = settings.toggle;
    return proxied.call(this, $.extend({}, settings, {
      collapsed: true,
      toggle: function () {
        var $this = $(this);
        if ($this.hasClass("hasChildren")) {
          var subFolder = $(this).find("a").attr("href").split("?")[0];
          var ajaxUrl = {
            url: "?vrtx=admin&" + settings.service + "&uri=" + subFolder + "&ts=" + (+new Date())
          };
          $.extend(settings, ajaxUrl);
          var childList = $this.removeClass("hasChildren").find("ul");
          load(settings, this.id, childList, container);
        }
        if (userToggle) {
          userToggle.apply(this, arguments);
        }
      }
    }));
  };

})(jQuery);
