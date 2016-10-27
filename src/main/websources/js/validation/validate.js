function validate() {
  this.isDebugMode = false,
  this.run = function() {
    // Vortex fields that needs validation
    Validate.validate({ date: ".start-date" }, "TIME_HELP");
    Validate.validate({ date: ".end-date" }, "TIME_HELP");
    Validate.validate({ startDate: ".start-date",
                        endDate: ".end-date" }, "DATE_RANGE");
  };
  this.validate = function(selector, type) {
    var vd = this;

    if(vd.isDebugMode) $(selector.date).css("border", "2px solid red");

    switch (type) {
      case "TIME_HELP":
        $(document).on("change", selector.date + " .vrtx-hours", function () {
          var hh = $(this);
          var mm = hh.nextAll(".vrtx-minutes").filter(":first");
          vd.timeHelp(hh, mm);
        });
        $(document).on("change", selector.date + " .vrtx-minutes", function () {
          var mm = $(this);
          var hh = mm.prevAll(".vrtx-hours").filter(":first");
          vd.timeHelp(hh, mm);
        });
        break;
      case "DATE_RANGE":
        $(document).on("focus", selector.endDate + " input[type='text']", function () {
          var parent = $(this).closest(selector.endDate);
          var isEndBlank = true;
          parent.find("input[type='text']").filter(":visible").each(function() {
            if(this.value != "") isEndBlank = false;
          });
          if(isEndBlank) {
            var isStartBlank = false;
            var startDateTime = parent.prev(selector.startDate);
            startDateTime.find("input[type='text']").filter(":visible").each(function() {
              if(this.value === "") isStartBlank = true;
            });
            if(!isStartBlank) {
              var startDate = startDateTime.find(".vrtx-date");
              var endDate = parent.find(".vrtx-date");
              endDate.val(startDate.val());

              var startHours = startDateTime.find(".vrtx-hours");
              var startHoursVal = parseInt(startHours.val(), 10);
              var endHours = parent.find(".vrtx-hours");
              endHours.val((startHoursVal < 23 ? (startHoursVal + 1) : 23));

              var startMinutes = startDateTime.find(".vrtx-minutes");
              var startMinutesVal = startMinutes.val();
              var endMinutes = parent.find(".vrtx-minutes");
              endMinutes.val(startMinutesVal);
            }
          }
        });
        var currentStartDateVal = "";
        $(document).on("focus", selector.startDate + " .vrtx-date", function () {
          var startDate = $(this);
          currentStartDateVal = startDate.val();
        });
        $(document).on("change", selector.startDate + " .vrtx-date", function () {
          var parent = $(this).closest(selector.startDate);
          var startDate = $(this);
          var startDateVal = startDate.val();
          var endDateTime = parent.next(selector.endDate);
          var endDate = endDateTime.find(".vrtx-date");
          var endDateVal = endDate.val();

          if(currentStartDateVal === endDateVal && currentStartDateVal != "") {
            currentStartDateVal = startDateVal;
            endDate.val(currentStartDateVal);
          }
        });
        break;
      default:
    }
  };
  this.timeHelp = function(hh, mm) {
    var hhVal = hh.val();
    var mmVal = mm.val();
    if(hhVal.length || mmVal.length) {
      var newHhVal = this.timeRangeHelp(hhVal, 23);
      var newMmVal = this.timeRangeHelp(mmVal, 59);
      if((newHhVal == "00" || newHhVal == "0") && (newMmVal == "00" || newMmVal == "0")) { // If all zeroes => remove time
        hh.val("");
        mm.val("");
      } else {
        if(hhVal != newHhVal) hh.val(newHhVal);
        if(mmVal != newMmVal) mm.val(newMmVal);
      }
    }
  };
  this.timeRangeHelp = function(val, max) {
    var newVal = parseInt(val, 10);
    if(isNaN(newVal) || newVal < 0) {
      newVal = "00";
    } else {
      newVal = (newVal > max) ? "00" : newVal;
      newVal = ((newVal < 10 && !newVal.length) ? "0" : "") + newVal;
    }
    return newVal;
  };
}

var Validate = new validate();
