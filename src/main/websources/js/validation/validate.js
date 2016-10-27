function validate() {
  this.isDebugMode = false,
  this.run = function() {
    // Vortex fields that needs validation
    Validate.validate(".start-date", "DATE_RANGE");
    Validate.validate(".end-date", "DATE_RANGE");
  };
  this.validate = function(selector, type) {
    var vd = this;
    
    if(vd.isDebugMode) $(selector).css("border", "2px solid red");

    switch (type) {
      case "DATE_RANGE":
        $(document).on("change", ".vrtx-hours", function () {
          var hh = $(this);
          var mm = hh.nextAll(".vrtx-minutes").filter(":first");
          vd.timeHelp(hh, mm);
        });
        $(document).on("change", ".vrtx-minutes", function () {
          var mm = $(this);
          var hh = mm.prevAll(".vrtx-hours").filter(":first");
          vd.timeHelp(hh, mm);
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
