/*
 * Specific behavior of datepicker for event-listing
 * 
 * TODO: Better interchange between format '2010-4-2' and '2010-04-02'
 * 
 */

function eventListingCalendar(allowedDates, activeDate, clickableDayTitle, notClickableDayTitle, language) {

  var activeDateForInit = makeActiveDateForInit(activeDate);
  var defaultDate = (activeDateForInit == null) ? null : activeDateForInit[0];
  
  // i18n (default english)
  if (language == 'no') {
    $.datepicker.setDefaults($.datepicker.regional['no']);
  } else if (language == 'nn') {
    $.datepicker.setDefaults($.datepicker.regional['nn']);
  }
  
  $("#datepicker").datepicker( {
    dateFormat : 'yy-mm-dd',
    onSelect : function(dateText, inst) {
      location.href = location.href.split('?')[0] + "?date=" + dateText;
    },
    firstDay : 1,
    showOtherMonths: true,
    defaultDate : defaultDate,
    beforeShowDay : function(day) { // iterates days in month
      // Add classes and tooltip for dates with and without events
      var date_str = $.datepicker.formatDate("yy-m-d", new Date(day)).toString();
      
      if ($.inArray(date_str, allowedDates) != -1) { // If a day in month has event
    	// Format correct
    	if(activeDateForInit[1] == 3) {
    	  var choosen = $.datepicker.formatDate("yy-m-d", activeDateForInit[0]).toString();
    	} else if(activeDateForInit[1] == 2) {
    	  var choosen = $.datepicker.formatDate("yy-m", activeDateForInit[0]).toString();
    	} else if (activeDateForInit[1] == 3) {
    	  var choosen = $.datepicker.formatDate("yy", activeDateForInit[0]).toString();
    	}

        if (choosen == date_str) {
          return [ true, 'state-active', clickableDayTitle ]; // If today has events
        } else {
          return [ true, '', clickableDayTitle ];
        }
      } else {
          return [ false, '', notClickableDayTitle ];
      }
    },
    onChangeMonthYear : function(year, month, inst) {
      var date = $.datepicker.formatDate("yy-mm", new Date(year, month - 1)).toString();      
      location.href = "./?date=" + date;
    }
  });
  
  var interval = 25;
  var checkMonthYearHTMLLoaded = setInterval(function() {
	if($(".ui-datepicker-month").length && $(".ui-datepicker-year").length) {
  	  var date = $.datepicker.formatDate("yy-mm", new Date(activeDateForInit)).toString();
  	  
  	  $(".ui-datepicker-month")
  	    .html("<a href='./?date=" + date + "'>" 
  	      + $(".ui-datepicker-month").text() + ' ' 
  	      + $(".ui-datepicker-year").remove().text() 
  	      + "</a>");
  	  
  	  clearInterval(checkMonthYearHTMLLoaded);
    }
  }, interval);

}

// For init of calender / datepicker()
function makeActiveDateForInit(activeDate) { 
  if(activeDate == "") { return null; }
  var dateArray = activeDate.split('-');
  var len = dateArray.length;
  if (len == 3) {
    return [new Date(dateArray[0], dateArray[1] - 1, dateArray[2]), len];
  } else if (len == 2) {
    return [new Date(dateArray[0], dateArray[1] - 1), len];
  } else if (len == 1) {
    return [new Date(dateArray[0]), len];
  }
}