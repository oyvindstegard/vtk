/*
 * Autocomplete - jQuery plugin 1.0.2
 *
 * Copyright (c) 2007 Dylan Verheul, Dan G. Switzer, Anjesh Tuladhar, Jörn Zaefferer
 *
 * Dual licensed under the MIT and GPL licenses:
 *   http://www.opensource.org/licenses/mit-license.php
 *   http://www.gnu.org/licenses/gpl.html
 *
 * Revision: $Id: jquery.autocomplete.js 5747 2008-06-25 18:30:55Z joern.zaefferer $
 *
 */

/*
 * Extensions added by USIT for Vortex
 * 
 * [???. ????] Added RIGHTARROW & SPACE as selection keys
 * [???. ????] Added option "resultsBeforeScroll" (defines minimum nr of hits before scrollbar is added to dropdown)
 * [???. ????] Added class when submit is blocked in FF for use externally (removed when intercepted in reroute-function in admin-enhancements and toggled off and does not interfere with anything else)
 * [???. ????] Added option "adjustForParentWidth"
 * [???. ????] Added option "min-width"
 * [???. ????] Added class "ac_active_parent" for active autocomplete field (to solve stacking issues with multiple fields)
 * [Oct. 2013] Added option "wrapperClass" possibility to distinguish between different autocomplete results
 * [Oct. 2013] Added class "ac_more" for special case in result when more-link is needed (if formatted starts with "###MORE###LINK###")
 * [Oct. 2013] Added option "finiteScroll" for not scrolling to top from bottom and vice versa on key/page up and down (set default to true)
 * [Oct. 2013] Added string constants to CLASSES object-literal for all CSS classes inside $.Autocompleter.Select
 * [Oct. 2013] Added option "updateInput" to make it possible to disable updating of input field on select (set default to true)
 * [Mar. 2014] Added ARIA status message and busy attribute to autocomplete field when loading
 */

;
( function($) {

  $.fn.extend( {
    autocomplete : function(urlOrData, options) {
      var isUrl = typeof urlOrData == "string";
      options = $.extend( {}, $.Autocompleter.defaults, {
        url :isUrl ? urlOrData : null,
        data :isUrl ? null : urlOrData,
        delay :isUrl ? $.Autocompleter.defaults.delay : 10,
        max :options && !options.scroll ? 10 : 150
      }, options);

      // if highlight is set to false, replace it with a do-nothing function
    options.highlight = options.highlight || function(value) {
      return value;
    };

    // if the formatMatch option is not specified, then use formatItem for
    // backwards compatibility
    options.formatMatch = options.formatMatch || options.formatItem;

    return this.each( function() {
      new $.Autocompleter(this, options);
    });
  },
  result : function(handler) {
    return this.bind("result", handler);
  },
  search : function(handler) {
    return this.trigger("search", [ handler ]);
  },
  flushCache : function() {
    return this.trigger("flushCache");
  },
  setOptions : function(options) {
    return this.trigger("setOptions", [ options ]);
  },
  unautocomplete : function() {
    return this.trigger("unautocomplete");
  }
  });

  $.Autocompleter = function(input, options) {

    var KEY = {
      UP :38,
      DOWN :40,
      DEL :46,
      TAB :9,
      RETURN :13,
      ESC :27,
      COMMA :188,
      PAGEUP :33,
      PAGEDOWN :34,
      BACKSPACE :8,
      RIGHTARROW :39,
      SPACE :32
    };

    // Create $ object for input element
    var $input = $(input).attr("autocomplete", "off").addClass(options.inputClass);

    var timeout;
    var previousValue = "";
    var cache = $.Autocompleter.Cache(options);
    var hasFocus = 0;
    var lastKeyPressCode;
    var config = {
      mouseDownOnSelect :false
    };
    var select = $.Autocompleter.Select(options, input, selectCurrent, config);

    var blockSubmit;

    // prevent form submit in opera when selecting with return key
    $.browser.opera && $(input.form).bind("submit.autocomplete", function() {
      if (blockSubmit) {
        blockSubmit = false;
        return false;
      }
    });
    
    // only opera doesn't trigger keydown multiple times while pressed, others
    // don't work with keypress at all
    $input.bind(($.browser.opera ? "keypress" : "keydown") + ".autocomplete", function(event) {
      // track last key pressed
        lastKeyPressCode = event.keyCode;
        switch (event.keyCode) {

        case KEY.UP:
          event.preventDefault();
          if (select.visible()) {
            select.prev();
          } else {
            onChange(0, true);
          }
          break;

        case KEY.DOWN:
          event.preventDefault();
          if (select.visible()) {
            select.next();
          } else {
            onChange(0, true);
          }
          break;

        case KEY.PAGEUP:
          event.preventDefault();
          if (select.visible()) {
            select.pageUp();
          } else {
            onChange(0, true);
          }
          break;

        case KEY.PAGEDOWN:
          event.preventDefault();
          if (select.visible()) {
            select.pageDown();
          } else {
            onChange(0, true);
          }
          break;

        case KEY.RIGHTARROW:
        case KEY.TAB:
        case KEY.RETURN:
          if (selectCurrent()) {
            // stop default to prevent a form submit, Opera needs special
        // handling
        event.preventDefault();
        blockSubmit = true;
        if(!$input.hasClass("blockSubmit") && $.browser.mozilla) {
          $input.addClass("blockSubmit");
        }
        return false;
      }
      break;

    case KEY.ESC:
      select.hide();
      break;

    default:
      clearTimeout(timeout);
      timeout = setTimeout(onChange, options.delay);
      break;
    }
  } ).focus( function() {
      // track whether the field has focus, we shouldn't process any
        // results if the field no longer has focus
        hasFocus++;
      }).blur( function() {
      hasFocus = 0;
      if (!config.mouseDownOnSelect) {
        hideResults();
      }
    }).click( function() {
      // show select when clicking in a focused field
        if (hasFocus++ > 1 && !select.visible()) {
          onChange(0, true);
        }
      }).bind("search", function() {
      // TODO why not just specifying both arguments?
        var fn = (arguments.length > 1) ? arguments[1] : null;
        function findValueCallback(q, data) {
          var result;
          if (data && data.length) {
            for ( var i = 0; i < data.length; i++) {
              if (data[i].result.toLowerCase() == q.toLowerCase()) {
                result = data[i];
                break;
              }
            }
          }
          if (typeof fn == "function")
            fn(result);
          else
            $input.trigger("result", result && [ result.data, result.value ]);
        }
        $.each(trimWords($input.val()), function(i, value) {
          request(value, findValueCallback, findValueCallback);
        });
      }).bind("flushCache", function() {
      cache.flush();
    }).bind("setOptions", function() {
      $.extend(options, arguments[1]);
      // if we've updated the data, repopulate
        if ("data" in arguments[1])
          cache.populate();
      }).bind("unautocomplete", function() {
      select.unbind();
      $input.unbind();
      $(input.form).unbind(".autocomplete");
    });

    function selectCurrent() {
      var selected = select.selected();
      if (!selected)
        return false;

      var v = selected.result;
      previousValue = v;

      if(options.updateInput) {
        if (options.multiple) {
          var words = trimWords($input.val());
          if (words.length > 1) {
            v = words.slice(0, words.length - 1).join(options.multipleSeparator) + options.multipleSeparator + v;
          }
          v += options.multipleSeparator;
        }
        $input.val(v);
      }
      
      hideResultsNow();
      $input.trigger("result", [ selected.data, selected.value ]);
      return true;
    }

    function onChange(crap, skipPrevCheck) {
      if (lastKeyPressCode == KEY.DEL) {
        select.hide();
        return;
      }

      var currentValue = $input.val();

      if (!skipPrevCheck && currentValue == previousValue)
        return;

      var delimiter = $.trim(options.multipleSeparator);
      currentValue = $.trim(currentValue);
      if (currentValue.match(delimiter + "$") == delimiter) {
        stopLoading();
        select.hide();
        return;
      }

      previousValue = currentValue;

      currentValue = lastWord(currentValue);
      if (currentValue.length >= options.minChars) {
        $input.addClass(options.loadingClass);
        $input.attr("aria-busy", "true");
        if (!options.matchCase)
          currentValue = currentValue.toLowerCase();
        request(currentValue, receiveData, hideResultsNow);
      } else {
        stopLoading();
        select.hide();
      }
    }
    ;

    function trimWords(value) {
      if (!value) {
        return [ "" ];
      }
      var words = value.split(options.multipleSeparator);
      var result = [];
      $.each(words, function(i, value) {
        if ($.trim(value))
          result[i] = $.trim(value);
      });
      return result;
    }

    function lastWord(value) {
      if (!options.multiple)
        return value;
      var words = trimWords(value);
      return words[words.length - 1];
    }

    // fills in the input box w/the first match (assumed to be the best match)
    // q: the term entered
    // sValue: the first matching result
    function autoFill(q, sValue) {
      // autofill in the complete box w/the first match as long as the user
      // hasn't entered in more data
      // if the last user key pressed was backspace, don't autofill
      if (options.autoFill && (lastWord($input.val()).toLowerCase() == q.toLowerCase())
          && lastKeyPressCode != KEY.BACKSPACE) {
        // fill in the value (keep the case the user has typed)
        $input.val($input.val() + sValue.substring(lastWord(previousValue).length));
        // select the portion of the value not typed by the user (so the next
        // character will erase)
        $.Autocompleter.Selection(input, previousValue.length, previousValue.length + sValue.length);
      }
    }
    ;

    function hideResults() {
      clearTimeout(timeout);
      timeout = setTimeout(hideResultsNow, 200);
    }
    ;

    function hideResultsNow() {
      var wasVisible = select.visible();
      select.hide();
      clearTimeout(timeout);
      stopLoading();
      if (options.mustMatch) {
        // call search and run callback
        $input.search( function(result) {
          // if no value found, clear the input box
            if (!result) {
              if (options.multiple) {
                var words = trimWords($input.val()).slice(0, -1);
                $input.val(words.join(options.multipleSeparator) + (words.length ? options.multipleSeparator : ""));
              } else
                $input.val("");
            }
          });
      }
      if (wasVisible)
        // position cursor at end of input field
        $.Autocompleter.Selection(input, input.value.length, input.value.length);
    }
    ;

    function receiveData(q, data) {
      if (data && data.length && hasFocus) {
        stopLoading();
        select.display(data, q);
        autoFill(q, data[0].value);
        select.show();
      } else {
        hideResultsNow();
      }
    }
    ;

    function request(term, success, failure) {
      if (!options.matchCase)
        term = term.toLowerCase();
      var data = cache.load(term);
      // recieve the cached data
      if (data && data.length) {
        success(term, data);
        // if an AJAX url has been supplied, try loading the data now
      } else if ((typeof options.url == "string") && (options.url.length > 0)) {

        var extraParams = {
          timestamp :+new Date()
        };
        $.each(options.extraParams, function(key, param) {
          extraParams[key] = typeof param == "function" ? param() : param;
        });

        $.ajax( {
          // try to leverage ajaxQueue plugin to abort previous requests
          mode :"abort",
          // limit abortion to this input
          port :"autocomplete" + input.name,
          dataType :options.dataType,
          url :options.url,
          data :$.extend( {
            q : options.termFormat ? options.termFormat.replace("%term%", lastWord(term)) : lastWord(term),
            limit :options.max
          }, extraParams),
          success : function(data) {
            var parsed = options.parse && options.parse(data) || parse(data);
            cache.add(term, parsed);
            success(term, parsed);
          }
        });
      } else {
        // if we have a failure, we need to empty the list -- this prevents the
        // the [TAB] key from selecting the last successful match
        select.emptyList();
        failure(term);
      }
    }
    ;

    function parse(data) {
      var parsed = [];
      var rows = data.split("\n");
      for ( var i = 0; i < rows.length; i++) {
        var row = $.trim(rows[i]);
        if (row) {
          row = row.split("|");
          parsed[parsed.length] = {
            data :row,
            value :row[0],
            result :options.formatResult && options.formatResult(row, row[0]) || row[0]
          };
        }
      }
      return parsed;
    }
    ;

    function stopLoading() {
      $input.removeClass(options.loadingClass);
      $input.attr("aria-busy", "false");
    }
    ;

  };

  $.Autocompleter.defaults = {
    inputClass: "ac_input",
    resultsClass: "ac_results",
    loadingClass: "ac_loading",
    wrapperClass: "",
    minChars: 1,
    delay: 400,
    matchCase: false,
    matchSubset: false,
    matchContains: false,
    cacheLength: 10,
    max: 100,
    mustMatch: false,
    extraParams: {},
    selectFirst: true,
    formatItem: function(row) {
      return row[0];
    },
    formatMatch: null,
    autoFill: false,
    width: 0,
    multiple: false,
    multipleSeparator: ", ",
    highlight: function(value, term) {
      return value.replace(new RegExp("^(?![^&;]+;)(?!<[^<>]*)("
          + term.replace(/([\^\$\(\)\[\]\{\}\*\.\+\?\|\\])/gi, "\\$1") + ")(?![^<>]*>)(?![^&;]+;)", "gi"),
          "<strong>$1</strong>");
    },
    scroll: true,
    scrollHeight: 180,
    resultsBeforeScroll: 10,
    minWidth: null,
    adjustForParentWidth: null,
    finiteScroll: true,
    updateInput: true,
    adjustLeft: 0
  };

  $.Autocompleter.Cache = function(options) {

    var data = {};
    var length = 0;

    function matchSubset(s, sub) {
      if (!options.matchCase)
        s = s.toLowerCase();
      var i = s.indexOf(sub);
      if (i == -1)
        return false;
      return i == 0 || options.matchContains;
    }
    ;

    function add(q, value) {
      if (length > options.cacheLength) {
        flush();
      }
      if (!data[q]) {
        length++;
      }
      data[q] = value;
    }

    function populate() {
      if (!options.data)
        return false;
      // track the matches
      var stMatchSets = {}, nullData = 0;

      // no url was specified, we need to adjust the cache length to make sure
      // it fits the local data store
      if (!options.url)
        options.cacheLength = 1;

      // track all options for minChars = 0
      stMatchSets[""] = [];

      // loop through the array and create a lookup structure
      for ( var i = 0, ol = options.data.length; i < ol; i++) {
        var rawValue = options.data[i];
        // if rawValue is a string, make an array otherwise just reference the
        // array
        rawValue = (typeof rawValue == "string") ? [ rawValue ] : rawValue;

        var value = options.formatMatch(rawValue, i + 1, options.data.length);
        if (value === false)
          continue;

        var firstChar = value.charAt(0).toLowerCase();
        // if no lookup array for this character exists, look it up now
        if (!stMatchSets[firstChar])
          stMatchSets[firstChar] = [];

        // if the match is a string
        var row = {
          value: value,
          data: rawValue,
          result: options.formatResult && options.formatResult(rawValue) || value
        };

        // push the current match into the set list
        stMatchSets[firstChar].push(row);

        // keep track of minChars zero items
        if (nullData++ < options.max) {
          stMatchSets[""].push(row);
        }
      }
      ;

      // add the data items to the cache
      $.each(stMatchSets, function(i, value) {
        // increase the cache size
          options.cacheLength++;
          // add to the cache
          add(i, value);
        });
    }

    // populate any existing data
    setTimeout(populate, 25);

    function flush() {
      data = {};
      length = 0;
    }

    return {
      flush: flush,
      add: add,
      populate: populate,
      load: function(q) {
        if (!options.cacheLength || !length)
          return null;
        /*
         * if dealing w/local data and matchContains than we must make sure to
         * loop through all the data collections looking for matches
         */
        if (!options.url && options.matchContains) {
          // track all matches
      var csub = [];
      // loop through all the data grids for matches
      for ( var k in data) {
        // don't search through the stMatchSets[""] (minChars: 0) cache
      // this prevents duplicates
      if (k.length > 0) {
        var c = data[k];
        $.each(c, function(i, x) {
          // if we've got a match, add it to the array
            if (matchSubset(x.value, q)) {
              csub.push(x);
            }
          });
      }
    }
    return csub;
  } else
  // if the exact item exists, use it
  if (data[q]) {
    return data[q];
  } else if (options.matchSubset) {
    for ( var i = q.length - 1; i >= options.minChars; i--) {
      var c = data[q.substr(0, i)];
      if (c) {
        var csub = [];
        $.each(c, function(i, x) {
          if (matchSubset(x.value, q)) {
            csub[csub.length] = x;
          }
        });
        return csub;
      }
    }
  }
  return null;
}
    };
  };

  $.Autocompleter.Select = function(options, input, select, config) {
    var CLASSES = {
      ACTIVE_PARENT: "ac_active_parent",
      ACTIVE: "ac_over",
      EVEN: "ac_even",
      ODD: "ac_odd",
      FIRST: "ac_first",
      LAST: "ac_last",
      MORE: "ac_more",
      DATA: "ac_data"
    };

    var listItems, active = -1, data, term = "", needsInit = true, element, list;

    // Create results
    function init() {
      if (!needsInit)
        return;
      element = $("<div/>").hide().addClass(options.resultsClass).addClass(options.wrapperClass).css("position", "absolute").appendTo(document.body);

      list = $("<ul/>").appendTo(element).mouseover( function(event) {
        if (target(event).nodeName && target(event).nodeName.toUpperCase() == 'LI') {
          active = $("li", list).removeClass(CLASSES.ACTIVE).index(target(event));
          $(target(event)).addClass(CLASSES.ACTIVE);
        }
      }).click( function(event) {
        $(target(event)).addClass(CLASSES.ACTIVE);
        select();
        // TODO provide option to avoid setting focus again after selection?
          // useful for cleanup-on-focus
          input.focus();
          return false;
        }).mousedown( function() {
        config.mouseDownOnSelect = true;
      }).mouseup( function() {
        config.mouseDownOnSelect = false;
      });
      if (options.width > 0)
        element.css("width", options.width);

      needsInit = false;
    }

    function target(event) {
      var element = event.target;
      while (element && element.tagName != "LI")
        element = element.parentNode;
      // more fun with IE, sometimes event.target is empty, just ignore it then
      if (!element)
        return [];
      return element;
    }

    function moveSelect(step) {
      listItems.slice(active, active + 1).removeClass(CLASSES.ACTIVE);
      movePosition(step);
      var activeItem = listItems.slice(active, active + 1).addClass(CLASSES.ACTIVE);
      if (options.scroll && (listItems.length > options.resultsBeforeScroll || options.resultsBeforeScroll == 0)) {
        var offset = 0;
        listItems.slice(0, active).each( function() {
          offset += this.offsetHeight;
        });
        if ((offset + activeItem[0].offsetHeight - list.scrollTop()) > list[0].clientHeight) {
          list.scrollTop(offset + activeItem[0].offsetHeight - list.innerHeight());
        } else if (offset < list.scrollTop()) {
          list.scrollTop(offset);
        }
      }
    }
    ;

    function movePosition(step) {
      active += step;
      
      var realLength = listItems.length - 1;
      if (active < 0) {
        active = !options.finiteScroll ? realLength : 0;
      } else if (active > realLength) {
        active = !options.finiteScroll ? 0 : realLength;
      }
    }

    function limitNumberOfItems(available) {
      return options.max && options.max < available ? options.max : available;
    }
    
    function addLi(formatted, cls, dt) {
      var li = $("<li/>").html(options.highlight(formatted, term)).addClass(cls).appendTo(list)[0];           
      $.data(li, CLASSES.DATA, dt);
    }
    
    function addCls(i, max) {
      return ((i % 2 == 0) ? CLASSES.EVEN : CLASSES.ODD)
           + ((i == (max - 1)) ? " " + CLASSES.LAST : "")
           + ((i == 0) ? " " + CLASSES.FIRST : "");
    }

    function fillList() {
      list.empty();
      var len = data.length;
      var max = limitNumberOfItems(len);

      var offset = 0;
      if(len > 0) {
        var j = len - 1;
        var formattedMoreLink = options.formatItem(data[j].data, j + 1, max, data[j].value, term);
        if(/^###MORE###LINK###.*$/.test(formattedMoreLink)) {
          formattedMoreLink = formattedMoreLink.replace(/^###MORE###LINK###[\s]*/, "");
          var dataMoreLink = data[j];
          var offset = 1;
        } else {
          formattedMoreLink = null;
        }
      }
      for ( var i = 0; i < (max-offset); i++) {
        if (!data[i])
            continue;
          
        var formatted = options.formatItem(data[i].data, i + 1, max, data[i].value, term);
        if (formatted === false)
          continue;
       
        addLi(formatted, addCls(i, max), data[i]);
      }
      
      if(formattedMoreLink != null) {
        addLi(formattedMoreLink, addCls(max-offset, max) + " " + CLASSES.MORE, dataMoreLink);
      }
      
      listItems = list.find("li");
      if (options.selectFirst) {
        listItems.slice(0, 1).addClass(CLASSES.ACTIVE);
        active = 0;
      }
      // apply bgiframe if available
      if ($.fn.bgiframe)
        list.bgiframe();
    }

    return {
      display : function(d, q) {
        init();
        data = d;
        term = q;
        fillList();
      },
      next: function() {
        moveSelect(1);
      },
      prev: function() {
        moveSelect(-1);
      },
      pageUp: function() {
        if (active != 0 && active - 8 < 0) {
          moveSelect(-active);
        } else {
          moveSelect(-8);
        }
      },
      pageDown: function() {
        if (active != listItems.length - 1 && active + 8 > listItems.length) {
          moveSelect(listItems.length - 1 - active);
        } else {
          moveSelect(8);
        }
      },
      hide: function() {
        element && element.hide();
        listItems && listItems.removeClass(CLASSES.ACTIVE);
        active = -1;
      },
      visible: function() {
        return element && element.is(":visible");
      },
      current: function() {
        return this.visible() && (listItems.filter("." + CLASSES.ACTIVE)[0] || options.selectFirst && listItems[0]);
      },
      show: function() {
        var inputField = $(input);
        var offset = inputField.offset();
        if(typeof options.width == "string" || options.width > 0) {
          var acWidth = (typeof options.width == "string") ? parseInt(options.width) : options.width;
        } else {
          var acWidth = $(input).width();
        }
        if(options.minWidth && acWidth < options.minWidth) {
          acWidth = options.minWidth;
        }
        if(options.adjustForParentWidth) {
          acWidth += options.adjustForParentWidth;
        }
        // Stack up active field
        var inputFieldParent = inputField.closest(".vrtx-textfield");
        if(inputFieldParent.length) {
          $("." + CLASSES.ACTIVE_PARENT).removeClass(CLASSES.ACTIVE_PARENT);
          if(!inputFieldParent.hasClass(CLASSES.ACTIVE_PARENT)) {
            inputFieldParent.addClass(CLASSES.ACTIVE_PARENT);
          }
        }
        
        element.css({
          width: acWidth,
          top: offset.top + input.offsetHeight,
          left: offset.left + options.adjustLeft
        }).show();
        
        // ARIA status msg
        var form = inputField.closest("form");
        var formFieldset = form.find("fieldset");
        if(formFieldset.length) {
          form = formFieldset;
        }
        var statusMsg = $("html").attr("lang") == "en" ? (listItems.length <= 0 ? "No results" 
                                                                                : (listItems.length + (listItems.length > 1 ? " results are" 
                                                                                                                            : " result is") + " available, use up and down arrow keys to navigate."))
                                                       : (listItems.length <= 0 ? "Ingen resultater" 
                                                                                : (listItems.length + (listItems.length > 1 ? " resultater" 
                                                                                                                            : " resultat") + " er tilgjengelig, bruk opp og ned piltaster for å navigere."));
                                                                                                                                                                                                                             
        var statusElem = form.find(".ui-helper-hidden-accessible");
        if(statusElem.length) {
          statusElem.text(statusMsg);
        } else {
          form.prepend('<span role="status" aria-live="polite" class="ui-helper-hidden-accessible">' + statusMsg + '</span>');
        }
        
        if (options.scroll && (listItems.length > options.resultsBeforeScroll || options.resultsBeforeScroll == 0)) {
          list.scrollTop(0);
          list.css({
            maxHeight :options.scrollHeight,
            overflow :'auto'
          });
          if ($.browser.msie && typeof document.body.style.maxHeight === "undefined") {
            var listHeight = 0;
            listItems.each( function() {
              listHeight += this.offsetHeight;
            });
            var scrollbarsVisible = listHeight > options.scrollHeight;
            list.css('height', scrollbarsVisible ? options.scrollHeight : listHeight);
            if (!scrollbarsVisible) {
              // IE doesn't recalculate width when scrollbar disappears
      listItems
          .width(list.width() - parseInt(listItems.css("padding-left")) - parseInt(listItems.css("padding-right")));
    }
  }

} else {
  list.css( {
    maxHeight: '100%',
    overflow: 'hidden'
  });
}
},
selected : function() {
var selected = listItems && listItems.filter("." + CLASSES.ACTIVE).removeClass(CLASSES.ACTIVE);
return selected && selected.length && $.data(selected[0], CLASSES.DATA);
},
emptyList : function() {
list && list.empty();
},
unbind : function() {
element && element.remove();
}
    };
  };

  $.Autocompleter.Selection = function(field, start, end) {
    if (field.createTextRange) {
      var selRange = field.createTextRange();
      selRange.collapse(true);
      selRange.moveStart("character", start);
      selRange.moveEnd("character", end);
      selRange.select();
    } else if (field.setSelectionRange) {
      field.setSelectionRange(start, end);
    } else {
      if (field.selectionStart) {
        field.selectionStart = start;
        field.selectionEnd = end;
      }
    }
    field.focus();
  };

})(jQuery);
