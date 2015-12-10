/*
 * View dropdown
 *
 */

if(typeof viewDropdown === "undefined") { // Avoid duplicate running code
  var viewDropdown = true;
  (function() {
    var doc = $(document);
    doc.ready(function() {
    
      /* Add missing id with random postfix to avoid duplicates */
      var addMissingId = function(elm, classPrefix) {
        if(elm[0].id) {
          var id = elm[0].id;
        } else {
          var id = classPrefix + Math.round((+new Date() + 1) * Math.random());
          elm.attr("id", id);
        }
        return id;
      };
      
      /* Dropdown ARIA states */
      var ariaDropdownState = function (link, wrp, isExpanded, isEnter) {
        if(isExpanded) {
          var firstInteractiveElem = wrp.find("a, input[type='button'], input[type='submit']").filter(":first");
          if(firstInteractiveElem.length && isEnter) {
            firstInteractiveElem.focus();
          }
        }
        wrp.attr("aria-hidden", !isExpanded);
        link.attr("aria-expanded", isExpanded);
      };
      
      /* Dropdown click events handler */
      var toggledOpenClosable = function(e) {
        var keyCode = (e.keyCode ? e.keyCode : e.which);
        var isEnter = keyCode === 13;
        if(e.type === "click" || isEnter) {
          var link = $(this);
          var container = link.parent();
          if(container.hasClass("vrtx-dropdown-component-toggled")) {
            link.toggleClass("active");
          }
          if(link.hasClass("vrtx-dropdown-close-link")) {
            var wrp = link.closest(".vrtx-dropdown-wrapper");
          } else {
            var wrp = link.next(".vrtx-dropdown-wrapper");
          }
          if(!container.hasClass("vrtx-dropdown-component-toggle")) {
            wrp.slideToggle("fast", function() {
              ariaDropdownState(link, wrp, wrp.is(":visible"), isEnter);
            });
          } else {
            wrp.toggleClass("activated");
            ariaDropdownState(link, wrp, wrp.is(":visible"), isEnter);
          }
          e.stopPropagation();
          e.preventDefault();
        }
      };
    
      /* Initialize dropdowns */
      var wrappers = $(".vrtx-dropdown-wrapper"),
          addMissingIdFunc = addMissingId;
      for(var i = wrappers.length; i--;) {
        var wrp = $(wrappers[i]);
        var link = wrp.prev();
     
        var idWrp = addMissingIdFunc(wrp, "vrtx-dropdown-wrapper-");
        var idLink = addMissingIdFunc(link, "vrtx-dropdown-link-");
        
        wrp.attr("aria-labelledby", idLink);
        link.attr({
          "aria-controls": idWrp,
          "aria-haspopup": "true"
        });
        
        ariaDropdownState(link, wrp, false, false); /* Invisible at init */
      }
      
      /* Listen for click events */
      doc.on("click keydown", ".vrtx-dropdown-component a.vrtx-dropdown-link, .vrtx-dropdown-component a.vrtx-dropdown-close-link", toggledOpenClosable);
      
      var dropdownForm = $(".vrtx-dropdown-form");
      if(dropdownForm.length) {
        dropdownForm.addClass("hidden");
        $(".vrtx-dropdown-form-link").addClass("visible");
        $(document).off("click", ".vrtx-dropdown-form-link")
                    .on("click", ".vrtx-dropdown-form-link", function(e) {
          $(this).prev(".vrtx-dropdown-form").submit();
          e.stopPropagation();
          e.preventDefault();
        });
      }
    });
  })();
}