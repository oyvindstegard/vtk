function validate() {
  this.isDebugMode = false,
  this.run = function() {
    // Vortex fields that needs validation

    // DATE RANGE
    Validate.validate({ date: ".start-date", startDate: ".start-date", endDate: ".end-date" }, "TIME_HELP");
    Validate.validate({ date: ".end-date", startDate: ".start-date", endDate: ".end-date" }, "TIME_HELP");
    Validate.validate({ startDate: ".start-date", endDate: ".end-date" }, "DATE_RANGE");

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
          vd.checkRange(selector, vd);
        });
        $(document).on("change", selector.date + " .vrtx-minutes", function () {
          var mm = $(this);
          var hh = mm.prevAll(".vrtx-hours").filter(":first");
          vd.timeHelp(hh, mm);
          vd.checkRange(selector, vd);
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
              var endDate = parent.find(".vrtx-date");
              var endHours = parent.find(".vrtx-hours");
              var endMinutes = parent.find(".vrtx-minutes");

              var startDateObj = vd.extractDateObj(startDateTime);
              startDateObj.setTime(startDateObj.getTime() + (1*60*60*1000)); // Add 1 hour

              endDate.val(startDateObj.getFullYear() + "-" + vd.timePad(startDateObj.getMonth() + 1) + "-" + vd.timePad(startDateObj.getDate()));
              endHours.val(vd.timePad(startDateObj.getHours()));
              endMinutes.val(vd.timePad(startDateObj.getSeconds()));
            }
          }
        });
        var currentStartDateVal = "";
        $(document).on("focus", selector.startDate + " .vrtx-date", function () {
          var startDate = $(this);
          currentStartDateVal = startDate.val();
        });
        $(document).on("change", selector.startDate + " .vrtx-date", function () {
          var startDateTime = $(this).closest(selector.startDate);
          var startDate = $(this);
          var startDateVal = startDate.val();
          var endDateTime = startDateTime.next(selector.endDate);
          var endDate = endDateTime.find(".vrtx-date");
          var endDateVal = endDate.val();

          if(currentStartDateVal === endDateVal && currentStartDateVal != "") {
            currentStartDateVal = startDateVal;
            endDate.val(currentStartDateVal);
          }
          vd.checkRange(selector, vd);
        });
        $(document).on("change", selector.endDate + " .vrtx-date", function() {
          vd.checkRange(selector, vd);
        });
        break;
      default:
    }
  };
  this.checkRange = function(selector, vd) {
    var startDateTime = $(selector.startDate);
    var endDateTime = $(selector.endDate);

    var startDateObj = vd.extractDateObj(startDateTime);
    var endDateObj = vd.extractDateObj(endDateTime);

    var container = startDateTime.closest(".timeAndPlace");
    var errorsElm = container.find("ul.errors");
    var errorHtml = "<ul class='errors'><li>" + vrtxAdmin.messages.editor.validation.errors.dateTime.endBeforeStart + "</li></ul>";
    if(startDateObj != null && endDateObj != null) {
      if(endDateObj < startDateObj) {
        if(errorsElm.length) {
          errorsElm.replaceWith(errorHtml);
        } else {
          container.append(errorHtml);
        }
      } else {
        errorsElm.remove();
      }
    } else {
      errorsElm.remove();
    }
  };
  this.extractDateObj = function(dateTime) {
    var dateArr = dateTime.find(".vrtx-date").val().split("-");
    if(dateArr.length !== 3) return null;

    var hours = parseInt(dateTime.find(".vrtx-hours").val(), 10);
    var minutes = parseInt(dateTime.find(".vrtx-minutes").val(), 10);

    return new Date(parseInt(dateArr[0], 10), parseInt(dateArr[1], 10) - 1, parseInt(dateArr[2], 10), hours || 0, minutes || 0, 0, 0);
  };
  this.timePad = function(v) {
    return v < 10 ? "0" + v : v;
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
