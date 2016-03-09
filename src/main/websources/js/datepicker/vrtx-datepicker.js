/*
 *  VrtxDatepicker - facade to jQuery UI datepicker (by USIT/GPL|GUAN)
 *  
 *  API: http://api.jqueryui.com/datepicker/
 *  
 *  * Requires Dejavu OOP library
 *  * Requires but Lazy-loads jQuery UI library (if not defined) on open
 *  * Lazy-loads jQuery UI language file if language matches on open (and not empty string or 'en')
 */

/* Public
 * ----------------------------------------------------------------------------------------
 * initialize(opts)
 * initFields(dateFields)                        - Initialize fields
 * prepareForSave()                              - Combine dates into field for saving
 *
 * Private
 * ----------------------------------------------------------------------------------------
 * __initField(name, selector)                   - Initialize field
 * __initDefaultEndDates()                       - Initialize default end dates
 * __setDefaultEndDate(startDateElm, endDateElm) - Set default end date
 * __initTimeHelp()                              - Initialize help for keeping time
 * __timeHelp(hh, mm)                            - Keeps time in range
 * __timeRangeHelp(val, max)                     - Helper for keeping time in range
 * __extractHoursFromDate(datetime)              - Extracts HH from date
 * __extractMinutesFromDate(datetime)            - Extracts MM from date
 */

var VrtxDatepicker = dejavu.Class.declare({
  $name: "VrtxDatepicker",
  $constants: {
    contentsDefaultSelector: "#contents",
    timeDate: "date",
    timeHours: "hours",  
    timeMinutes: "minutes",
    timeMaxLengths: {
      date: 10,
      hours: 2,
      minutes: 2
    }
  },
  __opts: {},
  initialize: function (opts) {
    var datepick = this;
    datepick.__opts = opts;
    datepick.__opts.contents = $(opts.selector || this.$static.contentsDefaultSelector);
    
    // TODO: rootUrl and jQueryUiVersion should be retrieved from Vortex config/properties somehow
    var rootUrl = "/vrtx/__vrtx/static-resources";
    var jQueryUiVersion = "1.10.4";
    
    var futureUi = $.Deferred();
    
    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;
    
    if (typeof $.ui === "undefined") {
      getScriptFn(rootUrl + "/jquery/plugins/ui/jquery-ui-" + jQueryUiVersion + ".custom/js/jquery-ui-" + jQueryUiVersion + ".custom.min.js").done(function () {
        futureUi.resolve();
      });
    } else {
      futureUi.resolve();
    }
    $.when(futureUi).done(function() {
      var futureDatepickerLang = $.Deferred();
      if (opts.language != "" && opts.language != "en" && !$.datepicker.regional[opts.language]) {
        getScriptFn(rootUrl + "/jquery/plugins/ui/jquery-ui-" + jQueryUiVersion + ".custom/js/jquery.ui.datepicker-" + opts.language + ".js").done(function() {
          futureDatepickerLang.resolve(); 
          $.datepicker.setDefaults($.datepicker.regional[opts.language]);
        });
      } else {
        futureDatepickerLang.resolve(); 
      }
      $.when(futureDatepickerLang).done(function() {
        datepick.initFields(datepick.__opts.contents.find(".date"));
        datepick.__initTimeHelp();
        datepick.__initDefaultEndDates();
        if(opts.after) opts.after();
      });
    });
  },
  initFields: function(dateFields) {
    for(var i = 0, len = dateFields.length; i < len; i++) {
      var dateField = dateFields[i];
      this.__initField(typeof dateField === "string" ? dateField : dateField.name, this.__opts.selector);
    }
  },
  __initField: function(name, selector) {
    var hours = "";
    var minutes = "";
    var date = [];
    var fieldName = name.replace(/\./g, '\\.');

    if(typeof selector !== "undefined") {
      var elem = $(selector + " #" + fieldName);
    } else {
      var elem = $("#" + fieldName);
    }
  
    if (elem.length) {
      hours = this.__extractHoursFromDate(elem[0].value);
      minutes = this.__extractMinutesFromDate(elem[0].value)
      date = new String(elem[0].value).split(" ");
    }

    var dateField =  "<input class='vrtx-textfield vrtx-" + this.$static.timeDate + "' type='text' maxlength='" + this.$static.timeMaxLengths.date + "' size='8' id='" + name + "-" + this.$static.timeDate + "' value='" + date[0] + "' />";
    var hoursField = "<input class='vrtx-textfield vrtx-" + this.$static.timeHours + "' type='text' maxlength='" + this.$static.timeMaxLengths.hours + "' size='1' id='" + name + "-" + this.$static.timeHours + "' value='" + hours + "' />";
    var minutesField = "<input class='vrtx-textfield vrtx-" + this.$static.timeMinutes + "' type='text' maxlength='" + this.$static.timeMaxLengths.minutes + "' size='1' id='" + name + "-" + this.$static.timeMinutes + "' value='" + minutes + "' />";
    elem.hide();
    elem.after(dateField + hoursField + "<span class='vrtx-time-seperator'>:</span>" + minutesField);
    $("#" + fieldName + "-" + this.$static.timeDate).datepicker({
      dateFormat: 'yy-mm-dd',
      /* fix buggy IE focus functionality: 
       * http://www.objectpartners.com/2012/06/18/jquery-ui-datepicker-ie-focus-fix/ */
      fixFocusIE: false,
      /* blur needed to correctly handle placeholder text */
      onSelect: function(dateText, inst) {
        this.fixFocusIE = true;
        $(this).blur().change().focus();
      },
      onClose: function(dateText, inst) {
        this.fixFocusIE = true;
        this.focus();
      },
      beforeShow: function(input, inst) {
        var result = $.browser.msie ? !this.fixFocusIE : true;
        this.fixFocusIE = false;
        return result;
      }
    });
  },
  __initDefaultEndDates: function() {
    var datepick = this;
    
    var startDateElm = this.__opts.contents.find("#start-date-date");
    var endDateElm = this.__opts.contents.find("#end-date-date");
    if (startDateElm.length && endDateElm.length) {
      if (startDateElm.datepicker('getDate') != null) {
        datepick.__setDefaultEndDate(startDateElm, endDateElm);
      }
      this.__opts.contents.on("change", "#start-date-date, #end-date-date", function () {
        datepick.__setDefaultEndDate(startDateElm, endDateElm);
      }); 
    }
  },
  __setDefaultEndDate: function(startDateElm, endDateElm) {
    var endDate = endDateElm.val();
    var startDate = startDateElm.datepicker('getDate');
    if (endDate == "") {
      endDateElm.datepicker('option', 'defaultDate', startDate);
    }
  },
  __initTimeHelp: function() {
    var datepick = this;
    
    datepick.__opts.contents.on("change", ".vrtx-" + datepick.$static.timeHours, function () {
      var hh = $(this);
      var mm = hh.nextAll(".vrtx-" + datepick.$static.timeMinutes).filter(":first"); // Relative to
      datepick.__timeHelp(hh, mm);
    });
    datepick.__opts.contents.on("change", ".vrtx-" + datepick.$static.timeMinutes, function () {
      var mm = $(this);
      var hh = mm.prevAll(".vrtx-" + datepick.$static.timeHours).filter(":first"); // Relative to
      datepick.__timeHelp(hh, mm);
    });
  },
  __timeHelp: function(hh, mm) {
    var hhVal = hh.val();
    var mmVal = mm.val();
    if(hhVal.length || mmVal.length) {
      var newHhVal = this.__timeRangeHelp(hhVal, 23);
      var newMmVal = this.__timeRangeHelp(mmVal, 59);
      if((newHhVal == "00" || newHhVal == "0") && (newMmVal == "00" || newMmVal == "0")) { // If all zeroes => remove time
        hh.val("");
        mm.val("");
      } else {
        if(hhVal != newHhVal) hh.val(newHhVal);
        if(mmVal != newMmVal) mm.val(newMmVal);
      }
    }
  },
  __timeRangeHelp: function(val, max) {
    var newVal = parseInt(val, 10);
    if(isNaN(newVal) || newVal < 0) {
      newVal = "00";
    } else {
      newVal = (newVal > max) ? "00" : newVal;
      newVal = ((newVal < 10 && !newVal.length) ? "0" : "") + newVal;
    }
    return newVal;
  },
  __extractHoursFromDate: function(datetime) {
    var a = new String(datetime);
    var b = a.split(" ");
    if (b.length > 1) {
      var c = b[1].split(":");
      if (c != null) {
        return c[0];
      }
    }
    return "";
  },
  __extractMinutesFromDate: function(datetime) {
    var a = new String(datetime);
    var b = a.split(" ");
    if (b.length > 1) {
      var c = b[1].split(":");
      if (c.length > 0) {
        var min = c[1];
        if (min != null) {
          return min;
        }
        // Hour has been specified, but no minutes.
        // Return "00" to properly display time.
        return "00";
      }
    }
    return "";
  },
  prepareForSave: function() {
    var dateFields = $(".date");
    for(var i = 0, len = dateFields.length; i < len; i++) {
      var dateFieldName = dateFields[i].name;
      if (!dateFieldName) return;

      var fieldName = dateFieldName.replace(/\./g, '\\.');

      var hours = $("#" + fieldName + "-" + this.$static.timeHours)[0];
      var minutes = $("#" + fieldName + "-" + this.$static.timeMinutes)[0];
      var date = $("#" + fieldName + "-" + this.$static.timeDate)[0];

      var savedVal = "";
    
      if (date && date.value.toString().length) {
        savedVal = date.value;
        if (hours && hours.value.toString().length) {
          savedVal += " " + hours.value;
          if (minutes.value && minutes.value.toString().length) {
           savedVal += ":" + minutes.value;
          }
        }
      }
      dateFields[i].value = savedVal;
    }
  }
});