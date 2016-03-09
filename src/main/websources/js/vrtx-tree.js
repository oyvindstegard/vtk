/*
 * VrtxTree - facade to TreeView async (by USIT/GPL|GUAN)
 *  
 *  * Requires Dejavu OOP library
 *  * Requires but Lazy-loads TreeView and ScrollTo libraries (if not defined) on open
 */

/* Public
 * ----------------------
 * initialize(opts)
 *
 * Private
 * ----------------------
 * __openLeaf()     - Open leaf based on array
 * __scrollToLeaf() - Scroll to leaf
 */

var VrtxTree = dejavu.Class.declare({
  $name: "VrtxTree",
  $constants: {
    leafScrollDelay: 250,
    leafScrollTopAdjust: 145,
    leafLoadingClass: "loading-tree-node",
    leafSelector: "> .hitarea" // From closest li
  },
  __opts: {},
  initialize: function(opts) {
    var tree = this;
    tree.__opts = opts;
    tree.__opts.pathNum = 0;
    
    // TODO: rootUrl should be retrieved from Vortex config/properties somehow
    var rootUrl = "/vrtx/__vrtx/static-resources";
    
    $.loadCSS(window.location.protocol + "//" + window.location.host + rootUrl + "/themes/default/report/jquery.treeview.css");
    var futureTree = $.Deferred();
    
    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
    
    if (typeof $.fn.treeview !== "function") {
      getScriptFn(window.location.protocol + "//" + window.location.host + rootUrl + "/jquery/plugins/jquery.treeview.js").done(function () {
        getScriptFn(window.location.protocol + "//" + window.location.host + rootUrl + "/jquery/plugins/jquery.treeview.async.js").done(function () {
          futureTree.resolve();
        });
      });
    } else {
      futureTree.resolve();
    }
    var futureScrollTo = $.Deferred();
    if(typeof $.fn.scrollTo !== "function" && tree.__opts.scrollToContent) {
      getScriptFn(window.location.protocol + "//" + window.location.host + rootUrl + "/jquery/plugins/jquery.scrollTo.min.js").done(function () {
        futureScrollTo.resolve();
      });
    } else {
      futureScrollTo.resolve();
    }
    $.when(futureTree, futureScrollTo).done(function() {
      opts.elem.treeview({
        animated: "fast",
        url: window.location.protocol + '//' + window.location.host + window.location.pathname + "?vrtx=admin&uri=&" + opts.service + "&ts=" + (+new Date()),
        service: opts.service,
        dataLoaded: function () {
          tree.__openLeaf();
        }
      });
    });
  },
  __openLeaf: function() {
    var tree = this;
    var checkLeafAvailable = setTimeout(function () {
      var curLoadingMsg = $("." + tree.$static.leafLoadingClass);
      curLoadingMsg.closest("li").attr("aria-busy", "false");
      curLoadingMsg.remove();
      var link = tree.__opts.elem.find("a[href$='" + tree.__opts.trav[tree.__opts.pathNum] + "']");
      if (link.length) {
        var closestLi = link.closest("li");
        var hit = closestLi.find(tree.$static.leafSelector);
        hit.click();
        if (tree.__opts.scrollToContent && (tree.__opts.pathNum === (tree.__opts.trav.length - 1))) {
          tree.__opts.elem.css("background", "none").fadeIn(200, function () {  // Scroll to last node
            tree.__scrollToLeaf(link, true);
          });
        } else {
          tree.__scrollToLeaf(link, false);
        }
        $("<span class='" + tree.$static.leafLoadingClass + "'>" + loadingSubfolders + "</span>").insertAfter(hit.next());
        closestLi.attr("aria-busy", "true");
        tree.__opts.pathNum++;
      } else {
        setTimeout(arguments.callee, 15);
      }
    }, 15);
  },
  __scrollToLeaf: function(link, isLast) {
    var tree = this;
    if (tree.__opts.scrollToContent) {
      var scrollOpts = {
        easing: "swing",
        queue: true,
        axis: 'y'
      };
      if(isLast) {
        scrollOpts.complete = tree.__opts.afterTrav(link);
      }
      var newTop = Math.max(0, (link.position().top - tree.$static.leafScrollTopAdjust));
      $(tree.__opts.scrollToContent).finish().scrollTo(newTop, tree.$static.leafScrollDelay, scrollOpts);
    }
  }
});