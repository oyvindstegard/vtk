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
 *                  2. uri / <a>
 *                  3. title on <a>
 *                  4. update settings.url on toggle()
 *
 */

;(function($) {

function load(settings, root, child, container) {
	function createNode(parent) {
                var linkOrPlainText = "";
                if(this.uri) {
                  if(this.title) {
                    linkOrPlainText = "<a href='" + this.uri 
                                    + "' title='" + this.title + "'>" 
                                    + this.text + "</a>"
                  } else {
                    linkOrPlainText = "<a href='" + this.uri + "'>" 
                                    + this.text + "</a>"
                  }
                } else {
                  linkOrPlainText = this.text;
                }
		var current = $("<li/>").attr("id", this.id || "")
                  .html("<span>" + linkOrPlainText + "</span>").appendTo(parent);

                if (this.listClasses) {
			current.addClass(this.listClasses);
		}
		if (this.spanClasses) {
			current.children("span").addClass(this.spanClasses);
		}
		if (this.expanded) {
			current.addClass("open");
		}
		if (this.hasChildren || this.children && this.children.length) {
			var branch = $("<ul/>").appendTo(current);
			if (this.hasChildren) {
				current.addClass("hasChildren");
				createNode.call({
					classes: "placeholder",
					text: "&nbsp;",
					children:[]
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
		success: function(response) {
			child.empty();
			$.each(response, createNode, [child]);
	        $(container).treeview({add: child});
	    }
	}, settings.ajax));
	/*
	$.getJSON(settings.url, {root: root}, function(response) {
		function createNode(parent) {
			var current = $("<li/>").attr("id", this.id || "").html("<span>" + this.text + "</span>").appendTo(parent);
			if (this.classes) {
				current.children("span").addClass(this.classes);
			}
			if (this.expanded) {
				current.addClass("open");
			}
			if (this.hasChildren || this.children && this.children.length) {
				var branch = $("<ul/>").appendTo(current);
				if (this.hasChildren) {
					current.addClass("hasChildren");
					createNode.call({
						classes: "placeholder",
						text: "&nbsp;",
						children:[]
					}, branch);
				}
				if (this.children && this.children.length) {
					$.each(this.children, createNode, [branch])
				}
			}
		}
		child.empty();
		$.each(response, createNode, [child]);
        $(container).treeview({add: child});
    });
    */
}

var proxied = $.fn.treeview;
$.fn.treeview = function(settings) {
	if (!settings.url) {
		return proxied.apply(this, arguments);
	}
	var container = this;
	if (!container.children().size())
		load(settings, "source", this, container);
	var userToggle = settings.toggle;
	return proxied.call(this, $.extend({}, settings, {
		collapsed: true,
		toggle: function() {
			var $this = $(this);
			if ($this.hasClass("hasChildren")) {
		        var ajaxUrl = {
		  		  url: "?vrtx=admin&service=subresource-retrieve&uri=" + $(this).find("a").attr("href")
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
