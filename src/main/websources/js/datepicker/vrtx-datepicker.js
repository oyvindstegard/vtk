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

    // TODO: VrtxComponents should be a super class with these checks
    var rootUrl = "/vrtx/__vrtx/static-resources";
    var jQueryUiVersion = "1.12.1";

    var getScriptFn = (typeof $.cachedScript === "function") ? $.cachedScript : $.getScript;

    if(typeof vrtxComponents === "undefined") {
      vrtxComponents = {};
      vrtxComponents.futureUi = $.Deferred();
      vrtxComponents.futureUiIsLoading = false;
    }
    if (typeof $.ui === "undefined") {
      if(!vrtxComponents.futureUiIsLoading) {
        vrtxComponents.futureUiIsLoading = true;
        getScriptFn(rootUrl + "/jquery/plugins/ui/jquery-ui-" + jQueryUiVersion + ".custom/jquery-ui.min.js").done(function () {
          vrtxComponents.futureUiIsLoading = false;
          vrtxComponents.futureUi.resolve();
        });
      }
    } else {
      vrtxComponents.futureUi.resolve();
    }

    $.when(vrtxComponents.futureUi).done(function() {
      var futureDatepickerLang = $.Deferred();
      if (opts.language != "" && opts.language != "en" && !$.datepicker.regional[opts.language]) {
        getScriptFn(rootUrl + "/jquery/plugins/ui/jquery-ui-" + jQueryUiVersion + ".custom/jquery.ui.datepicker-" + opts.language + ".js").done(function() {
          futureDatepickerLang.resolve();
          $.datepicker.setDefaults($.datepicker.regional[opts.language]);
        });
      } else {
        futureDatepickerLang.resolve();
      }
      $.when(futureDatepickerLang).done(function() {
        datepick.initFields(datepick.__opts.contents.find(".date"));
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
        var result = /.*(msie|rv:11|edge).*/.test(navigator.userAgent.toLowerCase()) ? !this.fixFocusIE : true;
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
