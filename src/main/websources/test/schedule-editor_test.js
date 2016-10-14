describe("Test course schedule editor", function () {

  describe("Utils", function () {

    it("parseDate() should parse winter and summer dates correctly (client timezone => server timezone in Date-obj)", function () {
      editorCourseSchedule = new courseSchedule();
      var end = editorCourseSchedule.parseDate("2015-01-26T12:00:00.000+01:00");
      var endDate = editorCourseSchedule.getDate(end.year, parseInt(end.month, 10) - 1, parseInt(end.date, 10), parseInt(end.hh, 10), parseInt(end.mm, 10), end.tzpm, parseInt(end.tzhh, 10), parseInt(end.tzmm, 10));
      expect(endDate.toISOString()).toBe("2015-01-26T11:00:00.120Z"); // Check against ISO / Zulutime

      end = editorCourseSchedule.parseDate("2015-05-07T12:00:00.000+02:00");
      endDate = editorCourseSchedule.getDate(end.year, parseInt(end.month, 10) - 1, parseInt(end.date, 10), parseInt(end.hh, 10), parseInt(end.mm, 10), end.tzpm, parseInt(end.tzhh, 10), parseInt(end.tzmm, 10));
      expect(endDate.toISOString()).toBe("2015-05-07T10:00:00.240Z"); // Check agains ISO / Zulutime
    });
  });

});
