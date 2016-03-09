/* New-Norwegian initialisation for the jQuery UI date picker plugin. */
/* Written by Øyvind Hatland (oyvind.hatland@usit.uio.no). */
jQuery(function($){
    $.datepicker.regional['nn'] = {
		closeText: 'Lukk',
        prevText: '&laquo;Forrige',
		nextText: 'Neste&raquo;',
		currentText: 'I dag',
        monthNames: ['Januar','Februar','Mars','April','Mai','Juni',
        'Juli','August','September','Oktober','November','Desember'],
        monthNamesShort: ['Jan','Feb','Mar','Apr','Mai','Jun',
        'Jul','Aug','Sep','Okt','Nov','Des'],
		dayNamesShort: ['Søn','Mån','Tys','Ons','Tor','Fre','Lau'],
		dayNames: ['Søndag','Måndag','Tysdag','Onsdag','Torsdag','Fredag','Laurdag'],
		dayNamesMin: ['Sø','Må','Ty','On','To','Fr','La'],
		weekHeader: 'Veke',
        dateFormat: 'yy-mm-dd',
		firstDay: 1,
		isRTL: false,
		showMonthAfterYear: false,
		yearSuffix: ''};
});
