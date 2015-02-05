/*
 * Initialize Datepicker - old documenttypes and folders
 *
 */

function initDatePicker(language) {

  // i18n (default english)
  if (language === 'no' || language === 'nn') {
    $.datepicker.setDefaults($.datepicker.regional[language]);
  }

  $(".date").datepicker({
    dateFormat: 'yy-mm-dd'
  });
  
  var startDateElm = $("#resource\\.start-date");
  var endDateElm = $("#resource\\.end-date");

  if (!startDateElm.length || !endDateElm.length) {
    return;
  }

  var startDate = startDateElm.datepicker('getDate');
  if (startDate != null) {
    setDefaultEndDate(startDateElm, endDateElm);
  }

  startDateElm.change(function () {
    setDefaultEndDate(startDateElm, endDateElm);
  });
}

function setDefaultEndDate(startDateElm, endDateElm) {
  var endDate = endDateElm.val();
  var startDate = startDateElm.datepicker('getDate');
  if (endDate == "") {
    endDateElm.datepicker('option', 'defaultDate', startDate);
  }
}

/* ^ Initialize Datepicker - old documenttypes and folders */