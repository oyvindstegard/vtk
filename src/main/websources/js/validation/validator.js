function validator() {
  this.isDebugMode = false,
  this.validate = function(opts, type, cbFn) {
    var vd = this;

    if(vd.isDebugMode) $(opts.date).css("border", "2px solid red");

    switch (type) {
      case "IS_IMAGE":
        if(opts.url === "") {
          vd.addErrorMsg(opts.container, "", false);
          cbFn(false);
          return;
        }

        var image = new Image();
        image.onload = function() {

          // Check for invalid images: http://stackoverflow.com/questions/9809015/image-onerror-event-never-fires-but-image-isnt-valid-data-need-a-work-around
          if (('naturalHeight' in this && (this.naturalHeight + this.naturalWidth === 0)) || (this.width + this.height == 0)) {
            this.onerror();
            return;
          }

          vd.addErrorMsg(opts.container, "", false);
          cbFn(true);
        };
        image.onerror = function() {
          vd.addErrorMsg(opts.container, vrtxAdmin.messages.editor.validation.errors.image.url, true);
          cbFn(false);
        };
        image.src = opts.url;

        break;
      case "TIME_HELP":
        $(document).on("change", opts.date + " .vrtx-hours", function () {
          var hh = $(this);
          var mm = hh.nextAll(".vrtx-minutes").filter(":first");
          vd.timeHelp(hh, mm);
          vd.checkRange(opts, vd);
        });
        $(document).on("change", opts.date + " .vrtx-minutes", function () {
          var mm = $(this);
          var hh = mm.prevAll(".vrtx-hours").filter(":first");
          vd.timeHelp(hh, mm);
          vd.checkRange(opts, vd);
        });

        break;
      case "DATE_RANGE":
        $(document).on("focus", opts.endDate + " input[type='text']", function () {
          var parent = $(this).closest(opts.endDate);
          var isEndBlank = true;
          parent.find("input[type='text']").filter(":visible").each(function() {
            if(this.value != "") isEndBlank = false;
          });
          if(isEndBlank) {
            var isStartBlank = false;
            var startDateTime = parent.prev(opts.startDate);
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
        $(document).on("focus", opts.startDate + " .vrtx-date", function () {
          var startDate = $(this);
          currentStartDateVal = startDate.val();
        });
        $(document).on("change", opts.startDate + " .vrtx-date", function () {
          var startDateTime = $(this).closest(opts.startDate);
          var startDate = $(this);
          var startDateVal = startDate.val();
          var endDateTime = startDateTime.next(opts.endDate);
          var endDate = endDateTime.find(".vrtx-date");
          var endDateVal = endDate.val();

          if(currentStartDateVal === endDateVal && currentStartDateVal != "") {
            currentStartDateVal = startDateVal;
            endDate.val(currentStartDateVal);
          }
          vd.checkRange(opts, vd);
        });
        $(document).on("change", opts.endDate + " .vrtx-date", function() {
          vd.checkRange(opts, vd);
        });

        break;
      default:
    }
  };
  this.addErrorMsg = function(container, msg, condition) {
    var errorsElm = container.find("ul.errors");
    var errorHtml = "<ul class='errors'><li>" + msg + "</li></ul>";
    if(condition) {
      if(errorsElm.length) {
        errorsElm.replaceWith(errorHtml);
      } else {
        container.append(errorHtml);
      }
    } else {
      errorsElm.remove();
    }
  };
  this.checkRange = function(opts, vd) {
    var startDateTime = $(opts.startDate);
    var endDateTime = $(opts.endDate);

    var startDateObj = vd.extractDateObj(startDateTime);
    var endDateObj = vd.extractDateObj(endDateTime);

    this.addErrorMsg(startDateTime.closest(".timeAndPlace"),
                     vrtxAdmin.messages.editor.validation.errors.dateTime.endBeforeStart,
                     (startDateObj != null && endDateObj != null && endDateObj < startDateObj));
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

var Validator = new validator();
