
/*
 * Schedule editor
 *
 */

function courseSchedule() {

  // Edit service
  var baseUrl = window.location.protocol + "//" + window.location.host + window.location.pathname;
  if(/\/$/.test(baseUrl)) {
    baseUrl += "index.html";
  }
  url = baseUrl + "?action=course-schedule&mode=edit&t=" + (+new Date());
  // Debug: Local development
  // url = "/vrtx/__vrtx/static-resources/js/tp-test.json";
  
  
  // Hide shortcut for saving working copy
  $("#vrtx-save-as-working-copy-shortcut, #saveWorkingCopyAction, #buttons-or-text").hide();

  // Data / lookup
  this.retrievedScheduleData = null;
  this.descs = {};
  this.isMedisin = false;
  this.vrtxResourcesFixedUrl = "";
  this.sessionsLookup = {};
  this.i18n = scheduleI18n;
  this.cancelled = false;
  this.embeddedAdminService = "?vrtx=admin&mode=actions-listing&types=resource&actions=view,edit-title,delete&global-actions=upload";
  
  // Last edited sessions
  this.lastId = "";
  this.lastSessionId = "";
  this.lastElm = null;
  this.returnSectionHash = "";

  // Get HTML for type ("plenary" or "group")
  this.getActivitiesForTypeHtml = function(type, isPlenary) {
    if(!this.retrievedScheduleData[type]) return "";
    var descsTmp = this.retrievedScheduleData[type].vrtxEditableDescription,
        data = this.retrievedScheduleData[type].activities;
    if(!descsTmp || !data) return "";
    
    this.descs[type] = descsTmp;
    var descs = this.descs[type];
        
    var dataLen = data.length;
    if(!dataLen) return "";
    
    // Store sessions HTML and multiple descriptions in lookup object
    var vrtxEdit = vrtxEditor,
        html = "",
        htmlArr = [], sessions = [], sequences = {}, // For fixed resources
        sessionsHtml = "";
    for(var i = 0; i < dataLen; i++) {
      var dt = data[i],
          teachingMethod = dt.teachingMethod.toLowerCase(),
          teachingMethodName = dt.teachingMethodName,
          id = teachingMethod + "-" + dt.id,
          title = isPlenary ? teachingMethodName : (dt.title || teachingMethodName),
          groupNumber = ((dt.party && dt.party.name) ? parseInt(dt.party.name, 10) : 0);

      this.sessionsLookup[id] = {};
      
      // Add together sessions from sequences
      for(var j = 0, len = dt.sequences.length; j < len; j++) {
        var sequence = dt.sequences[j];
        var fixedResources = sequence.vrtxResourcesFixed;
        if(fixedResources) {
          sequences[sequence.id] = jQuery.extend(true, [], fixedResources);
          this.deleteUnwantedFixedResourcesProps(sequence);
        }
        sessions = sessions.concat(sequence.sessions);
      }
      
      if(!isPlenary || (!data[i+1] || data[i+1].teachingMethod.toLowerCase() !== teachingMethod)) {
        // Evaluate and cache dateTime
        var map = [], sessionsProcessed = [];
        for(j = 0, len = sessions.length; j < len; j++) {
          var session = sessions[j];
          
          var dateTime = this.getDateTime(session.dtStart, session.dtEnd);
          var start = dateTime.start;
          var end = dateTime.end;
          var startEndString = start.year + "" + start.month + "" + start.date + "" + start.hh + "" + start.mm + "" + end.hh + "" + end.mm;
          
          map.push({
            "index": j, // Save index
            "startEndString": startEndString,
            "isOrphan": session.vrtxOrphan
          });
          sessionsProcessed.push({
            "dateTime": dateTime
          });
        }
        // Sort
        map.sort(function(a, b) {
          var x = a.isOrphan, y = b.isOrphan;
          if(x === y) {
            return a.startEndString > b.startEndString ? 1 : -1;
          }
          return !x && y ? -1 : x && !y ? 1 : 0;
        });
        
        // Generate sessions HTML (get correctly sorted from map)
        for(j = 0, len = map.length; j < len; j++) {
          var session = sessions[map[j].index];
          var sessionProcessed = sessionsProcessed[map[j].index];
          var sessionHtml = this.getSessionHtml(id, null, null, session, teachingMethod, sessionProcessed.dateTime, sequences, descs, isPlenary, vrtxEdit);
          sessionsHtml += vrtxEdit.htmlFacade.getAccordionInteraction(!isPlenary ? "5" : "4", sessionHtml.sessionId, "session", sessionHtml.title, sessionHtml.html);
        }
        
        if(isPlenary) {
          this.sessionsLookup[id].html = "<span class='accordion-content-title'>" + this.i18n.titles.activities + "</span>" + sessionsHtml;
          html += vrtxEdit.htmlFacade.getAccordionInteraction("3", id, (type + " skip-tier"), teachingMethodName, "");
        } else {
          this.sessionsLookup[id].html = "<span class='accordion-content-title'>" + this.i18n.titles.activities + "</span>" + sessionsHtml;
          htmlArr.push({ "teachingMethod": teachingMethod, "groupNr": groupNumber, "accHtml": vrtxEdit.htmlFacade.getAccordionInteraction("4", id, type, title, "") });
          
          if(!data[i+1] || data[i+1].teachingMethod.toLowerCase() !== teachingMethod) {
            // Sort group code and group number if equal
            htmlArr.sort(function(a, b) { // http://www.sitepoint.com/sophisticated-sorting-in-javascript/
              var x = a.teachingMethod, y = b.teachingMethod;
              if(x === y) {
                return a.groupNr - b.groupNr;
              }
              return x < y ? -1 : x > y ? 1 : 0;
            });
            var htmlMiddle = "";
            for(j = 0, len = htmlArr.length; j < len; j++) {
              htmlMiddle += htmlArr[j].accHtml;
            }
            if(htmlMiddle != "") {
              html += vrtxEdit.htmlFacade.getAccordionInteraction("3", teachingMethod, type, teachingMethodName, "<div class='vrtx-grouped'>" + htmlMiddle + "</div>");
            }
            htmlArr = [];
          }
        }
        sessionsHtml = "";
        sessions = [];
      }
    }
    
    delete this.retrievedScheduleData[type].vrtxEditableDescription;

    return html;
  };
  // Get HTML for single session
  this.getSessionOnlyHtml = function(sessionId) {
    var sessionData = this.getSessionJSONFromId(sessionId);
    if(!sessionData) return null;
    
    var session = sessionData.session;
    var descsTmp = this.retrievedScheduleData[sessionData.type].vrtxEditableDescription;
    
    if(!descsTmp) return null;
    
    this.descs[sessionData.type] = descsTmp;
    var descs = this.descs[sessionData.type];
    
    if(!this.sessionsLookup["single"]) {
      this.sessionsLookup["single"] = {};
    }
    var sessionHtml = this.getSessionHtml("single", sessionData.prevId, sessionData.nextId, session, sessionData.teachingMethod, sessionData.sessionDateTime,
                                          sessionData.sequences, descs, sessionData.isPlenary, vrtxEditor);    
    
    this.lastElm = $(".properties"); 
    this.lastId = "single";
    this.lastSessionId = "one";
    
    this.deleteUnwantedProps();
                                                    
    return { id: "single", isPlenary: sessionData.isPlenary, teachingMethod: sessionData.teachingMethod, html: sessionHtml.html, title: sessionHtml.title };
  };
  // Find single session
  this.getSessionJSONFromId = function(findSessionId) { // XXX: Refactor with getActivitiesForTypeHtml
    var foundObj = null;
    var nextId = null;
    var prevId = null;
    var sessions = [];
    var sequences = {};
    for(var type in this.retrievedScheduleData) {
      if(!this.retrievedScheduleData[type]) continue;
      var data = this.retrievedScheduleData[type].activities;
      if(!data) continue;
      var dataLen = data.length;
      if(!dataLen) continue;
      
      var isPlenary = type === "plenary";
      var groupsSessions = [];
      for(var i = 0; i < dataLen; i++) {
        var dt = data[i],
            teachingMethod = dt.teachingMethod.toLowerCase(),
            id = dt.id,
            groupNumber = ((dt.party && dt.party.name) ? parseInt(dt.party.name, 10) : 0)
        for(var j = 0, len = dt.sequences.length; j < len; j++) {
          var sequence = dt.sequences[j];
          var fixedResources = sequence.vrtxResourcesFixed;
          if(fixedResources) {
            sequences[sequence.id] = jQuery.extend(true, [], fixedResources);
          }
          sessions = sessions.concat(sequence.sessions);
        }
        if(!isPlenary || (!data[i+1] || data[i+1].teachingMethod.toLowerCase() !== teachingMethod)) {
          // Evaluate and cache dateTime
          var map = [], sessionsProcessed = [];
          for(j = 0, len = sessions.length; j < len; j++) {
            var session = sessions[j];
          
            var dateTime = this.getDateTime(session.dtStart, session.dtEnd);
            var start = dateTime.start;
            var end = dateTime.end;
            var startEndString = start.year + "" + start.month + "" + start.date + "" + start.hh + "" + start.mm + "" + end.hh + "" + end.mm;
          
            map.push({
              "index": j, // Save index
              "startEndString": startEndString,
              "isOrphan": session.vrtxOrphan
            });
            sessionsProcessed.push({
              "dateTime": dateTime
            });
          }
          // Sort
          map.sort(function(a, b) {
            var x = a.isOrphan, y = b.isOrphan;
            if(x === y) {
              return a.startEndString > b.startEndString ? 1 : -1;
            }
            return !x && y ? -1 : x && !y ? 1 : 0;
          });
          if(isPlenary) {
            for(j = 0, len = map.length; j < len; j++) {
              var session = sessions[map[j].index];
              var sessionProcessed = sessionsProcessed[map[j].index];
              var sessionDateTime = sessionProcessed.dateTime;
              var sessionDatePostFixId = this.getDateAndPostFixId(sessionDateTime);
              var sessionId = teachingMethod + "-" + session.id.replace(/\//g, "-").replace(/#/g, "-") + "-" + sessionDatePostFixId.postFixId;

              if(foundObj && !nextId) {
                nextId = sessionId;
                break;
              }
              if(findSessionId === sessionId) {
                this.returnSectionHash = "#" + teachingMethod.toUpperCase();
                foundObj = { prevId: prevId, session: session, sessionDateTime: sessionDateTime, sequences: sequences, type: type, isPlenary: isPlenary, teachingMethod: teachingMethod };
              } else {
                prevId = sessionId;
              }
            }
            sessions = [];
          } else {
            groupsSessions.push({ "teachingMethod": teachingMethod, "id": id, "groupNr": groupNumber, "sessions": sessions, "map": map, "sessionsProcessed": sessionsProcessed });
            sessions = [];
            if(!data[i+1] || data[i+1].teachingMethod.toLowerCase() !== teachingMethod) {
              // Sort group code and group number if equal
              groupsSessions.sort(function(a, b) { // http://www.sitepoint.com/sophisticated-sorting-in-javascript/
                var x = a.teachingMethod, y = b.teachingMethod;
                if(x === y) {
                  return a.groupNr - b.groupNr;
                }
                return x < y ? -1 : x > y ? 1 : 0;
              });
              for(k = 0, len1 = groupsSessions.length; k < len1; k++) {
                var groupSessions = groupsSessions[k];
                for(j = 0, len2 = groupSessions.map.length; j < len2; j++) {
                  var session = groupSessions.sessions[groupSessions.map[j].index];
                  var sessionProcessed = groupSessions.sessionsProcessed[groupSessions.map[j].index];
                  var sessionDateTime = sessionProcessed.dateTime;
                  var sessionDatePostFixId = this.getDateAndPostFixId(sessionDateTime);
                  var sessionId = groupSessions.teachingMethod + "-" + session.id.replace(/\//g, "-").replace(/#/g, "-") + "-" + sessionDatePostFixId.postFixId;

                  if(foundObj && !nextId) {
                    nextId = sessionId;
                    break;
                  }
                  if(findSessionId === sessionId) {
                    this.returnSectionHash = "#" + groupSessions.id;
                    foundObj = { prevId: prevId, session: session, sessionDateTime: sessionDateTime, sequences: sequences, type: type, isPlenary: isPlenary, teachingMethod: groupSessions.teachingMethod };
                  } else {
                    prevId = sessionId;
                  }
                }
                if(nextId) break;
              }
              groupSessions = [];
            }
          }
        }
        if(nextId) break;
      }
      if(nextId) break;
    }
    if(foundObj) {
      foundObj.nextId = nextId;
    }
    return foundObj;
  };
  // Get session HTML
  this.getSessionHtml = function(id, prevId, nextId, session, teachingMethod, sessionDateTime, sequences, descs, isPlenary, vrtxEdit) {
    var sessionDatePostFixId = this.getDateAndPostFixId(sessionDateTime);
    if(id === "single") {
      sessionId = "one";
    } else {
      sessionId = id + "-" + session.id.replace(/\//g, "-").replace(/#/g, "-") + "-" + sessionDatePostFixId.postFixId;
    }
    var sequenceIdSplit = session.id.split("/");
    if(sequenceIdSplit.length == 3) {
      var sequenceId = sequenceIdSplit[1];
    } else if(sequenceIdSplit == 2) {
      var sequenceId = sequenceIdSplit[0];
    } else {
      var sequenceId = sequenceIdSplit[0] || session.id;
    }
    var sessionOrphan = session.vrtxOrphan,
        sessionCancelled = !session.vrtxOrphan && (session.vrtxStatus && session.vrtxStatus === "cancelled") || (session.status && session.status === "cancelled"),
        rooms = session.rooms,
        sessionTitle = "<span class='session-date'>" + sessionDatePostFixId.date + "</span>" +
                       "<span class='session-title' data-orig='" + encodeURI(session.title || session.id) + "'>" + 
                       (sessionOrphan ? "<span class='header-status'>" + this.i18n.orphan + "</span> - " : "") +
                       (sessionCancelled ? "<span class='header-status'>" + this.i18n.cancelled + "</span> - " : "") +
                       "<span class='header-title'>" + (session.vrtxTitle || session.title || session.id) + "</span></span>" +
                       (rooms ? (" - <span class='session-room'>" + (rooms[0].buildingAcronym || rooms[0].buildingId) + " " + rooms[0].roomId) + "</span>" : "") +
                       ((prevId || nextId) ? "<div class='prev-next'>" : "") +
                       (prevId ? "<a class='prev' href='" + window.location.protocol + "//" + window.location.host + window.location.pathname + "?vrtx=admin&mode=editor&action=edit&embed&sessionid=" + prevId + "'>" + this.i18n.prev + "</a>" : "") +
                       (nextId ? "<a class='next' href='" + window.location.protocol + "//" + window.location.host + window.location.pathname + "?vrtx=admin&mode=editor&action=edit&embed&sessionid=" + nextId + "'>" + this.i18n.next + "</a>" : "") +
                       ((prevId || nextId) ? "</div>" : ""),
        sessionContent = vrtxEdit.htmlFacade.jsonToHtml(this.isMedisin, id, sessionId, id, session, this.vrtxResourcesFixedUrl, { "vrtxResourcesFixed": sequences[sequenceId] }, descs, this.i18n, this.embeddedAdminService);

     this.deleteUserResourcesEnrichments(session);
     var rawOrigTP = jQuery.extend(true, {}, session);

     if(!session.vrtxOrphan) {
       this.deleteUnwantedSessionProps(session);
     }
     
     this.sessionsLookup[id][sessionId] = {
       rawPtr: session,
       rawOrigTP: rawOrigTP,
       rawOrig: jQuery.extend(true, {}, session), // Copy object
       descsPtr: descs,
       multiples: sessionContent.multiples,
       rtEditors: sessionContent.rtEditors,
       sequenceId: sequenceId,
       isCancelled: sessionCancelled,
       isOrphan: sessionOrphan,
       isEnhanced: false,
       hasChanges: false
     };
     
     return { sessionId: sessionId, html: sessionContent.html, title: sessionTitle };
  };
  
  /* Dates */
  this.parseDate = function(dateString) {
    var unknownDate = { year: "1970", month: "01", date: "01", hh: "00", mm: "00", tzhh: "00", tzmm: "00" };
    if(typeof dateString !== "string") {
      return unknownDate;
    } else {
      var m = /^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})\.([0-9]{3})([+-])([0-9]{2}):([0-9]{2})$/.exec(dateString);
      if(m == null) {
        return unknownDate;
      } else {
        return { year: m[1], month: m[2], date: m[3], hh: m[4], mm: m[5], tzhh: m[9], tzmm: m[10] };
      }
    }
  };
  this.getDateTime = function(s, e) {
    var startDateTime = this.parseDate(s);
    var endDateTime = this.parseDate(e);
    return { start: startDateTime, end: endDateTime };
  };
  this.getDate = function(year, month, date, hh, mm, tzpm, tzhh, tzmm) {
    var date = new Date(year, month, date, hh, mm, 0, 0);
    
    var clientTimeZoneOffset = date.getTimezoneOffset();
    var serverTimeZoneOffset = (tzhh * 60) + tzmm;
    if(tzpm === "+") serverTimeZoneOffset = -serverTimeZoneOffset;
    
    if(clientTimeZoneOffset === serverTimeZoneOffset) return date; // Same offset in same date
    
    // Timezone correction offset for local time
    var offset = clientTimeZoneOffset > serverTimeZoneOffset ? clientTimeZoneOffset - serverTimeZoneOffset 
                                                             : serverTimeZoneOffset - clientTimeZoneOffset;
    return new Date(date.getTime() + offset);
  };
  this.getDateAndPostFixId = function(dateTime) {
    var start = dateTime.start;
    var end = dateTime.end;
    var endDate = this.getDate(end.year, parseInt(end.month, 10) - 1, parseInt(end.date, 10), parseInt(end.hh, 10), parseInt(end.mm, 10), end.tzpm, parseInt(end.tzhh, 10), parseInt(end.tzmm, 10));
    var endDay = this.i18n["d" + endDate.getDay()];
    var strDate = endDay.substring(0,2) + ". " + parseInt(start.date, 10) + ". " + this.i18n["m" + start.month] + ". - " + start.hh + ":" + start.mm + "-" + end.hh + ":" + end.mm;
    var postFixId = start.date + "-" + start.month + "-" + start.year + "-" + start.hh + "-" + start.mm + "-" + end.hh + "-" + end.mm;
    return { date: strDate, postFixId: postFixId };
  };
  
  // Enhance session fields (multiple and CK)
  this.enhanceSession = function(id, sessionId, contentElm) {
    var session = this.sessionsLookup[id][sessionId];
    if(session && !session.isEnhanced) { // If not already enhanced
      /* Multiple fields */
      var multiples = session.multiples;
      var enhanceMultipleInputFieldsFunc = enhanceMultipleInputFields;
      for(var i = multiples.length; i--;) {
        var m = multiples[i];
        enhanceMultipleInputFieldsFunc(m.name + "-" + sessionId, m.movable, m.browsable, 50, m.json, m.readOnly);
      }
      /* CKEditors */
      var rtEditors = session.rtEditors;
      for(i = rtEditors.length; i--;) {
        vrtxEditor.richtextEditorFacade.setup({
          name: rtEditors[i].name,
          isReadOnly: rtEditors[i].readOnly, 
          isCompleteEditor: false,
          isWithoutSubSuper: false,
          defaultLanguage: vrtxAdmin.lang,
          cssFileList: cssFileList,
          simple: true
        });
      }
      /* Load iframes for iframe placeholders */
      var iframePlaceholders = contentElm.find(".admin-fixed-resources-iframe");
      for(var i = iframePlaceholders.length; i--;) {
        var iframePlaceholder = $(iframePlaceholders[i]);
        var iframe = "<iframe class='admin-fixed-resources-iframe' src='" + iframePlaceholder.attr("data-src") + "' frameborder='0'></iframe>";
        iframePlaceholder.replaceWith(iframe);
      }
      
      session.isEnhanced = true;
    }
  };
  this.loadingUpdate = function(msg) {
    var loader = $("#editor-loading-inner");
    if(!loader.length) {
      var loaderHtml = "<span id='editor-loading'><span id='editor-loading-inner'>" + msg + "...</span></span>";
      $(loaderHtml).insertAfter(".properties");
    } else {
      if(msg.length) {
        loader.text(msg + "...");
      } else {
        loader.parent().remove();
        if(onlySessionId) {
          $("html").removeClass("embedded-loading");
          $("#editor").css("height", "auto");
        }
      }
    }
  };
  /*
   * DELETE unwanted properties from session and sequence
   */
  this.deleteUnwantedProps = function() {
    for(var type in this.retrievedScheduleData) {
      if(!this.retrievedScheduleData[type]) continue;
      var data = this.retrievedScheduleData[type].activities;
      if(!data) continue;
      var dataLen = data.length;
      if(!dataLen) continue;
      for(var i = 0; i < dataLen; i++) {
        var dt = data[i];
        var seqs = dt.sequences || [];
        for(var j = 0, seqsLen = seqs.length; j < seqsLen; j++) {
          var sequence = seqs[j];
          
          if(sequence.vrtxResourcesFixed) {
            this.deleteUnwantedFixedResourcesProps(sequence);
          }
          
          var sessions = sequence.sessions || [];
          for(var k = 0, sessLen = sessions.length; k < sessLen; k++) {
            if(!sessions[k].vrtxOrphan) {
              this.deleteUnwantedSessionProps(sessions[k]);
              this.deleteUserResourcesEnrichments(sessions[k]);
            } else {
              this.deleteUserResourcesEnrichments(sessions[k]);
            }
          }
        }
      }
      delete this.retrievedScheduleData[type].vrtxEditableDescription;
    }
  };
  /*
   * DELETE !folderUrl from objects in vrtxResourcesFixed (if no objects have folderUrl DELETE whole vrtxResourcesFixed)
   */
  this.deleteUnwantedFixedResourcesProps = function(sequence) {
    var newFixedResources = [];
    for(var i = 0; i < sequence.vrtxResourcesFixed.length; i++) {
      var fixedResources = sequence.vrtxResourcesFixed[i];
      var hasFolderUrl = false;
      for(var key in fixedResources) {
        if(key != "folderUrl") {
          delete fixedResources[key];
        } else {
          hasFolderUrl = true;
        }
      }
      if(hasFolderUrl) newFixedResources.push(fixedResources);
    }
    if (newFixedResources.length > 0) {
      sequence.vrtxResourcesFixed = newFixedResources;
    } else {
      delete sequence.vrtxResourcesFixed;
    }
  };
  /*
   * DELETE !(vrtx-props + id + dtStart + dtEnd) from a session
   */
  this.deleteUnwantedSessionProps = function(session) {
    for(var prop in session) {
      if(!/^vrtx/.test(prop) && prop != "id" && prop != "dtStart" && prop != "dtEnd") {
        delete session[prop];
      }
    }
  };
  /*
   * DELETE !(uid) from staff and vrtxStaff
   */
  this.deleteUserResourcesEnrichments = function(session) {
    if(session.staff) {
      for(var i = 0, len = session.staff.length; i < len; i++) {
        this.deleteUserEnrichment(session.staff[i]);
      }
    }
    if(session.vrtxStaff) {
      for(var i = 0, len = session.vrtxStaff.length; i < len; i++) {
        this.deleteUserEnrichment(session.vrtxStaff[i]);
      }
    }
    if(session.vrtxResources) {
      for(var i = 0, len = session.vrtxResources.length; i < len; i++) {
        this.deleteResourceEnrichment(session.vrtxResources[i]);
      }
    }
  };
  this.deleteUserEnrichment = function(user) {
    delete user.name;
    delete user.firstName;
    delete user.lastName;
    delete user.url;
  };
  this.deleteResourceEnrichment = function(resource) {
    delete resource.userReadRestricted;
  };
  this.checkUnsavedChanges = function() {
    if(this.cancelled) {
      return false;
    }
    this.saveLastSession();
    for(var type in this.sessionsLookup) {
      for(var session in this.sessionsLookup[type]) {
        if(this.sessionsLookup[type][session].hasChanges) {
          return true;
        }
      }
    }
    return false;
  };
  this.saveLastSession = function() {
    if(this.lastElm) {
      this.saveSession(this.lastElm, this.lastId, this.lastSessionId);
    }
  };
  this.saveSession = function(sessionElms, id, sessionId) {
    saveMultipleInputFields(sessionElms, "$$$");

    var sessionLookup = this.sessionsLookup[id][sessionId];
    var rawOrig = sessionLookup.rawOrig;
    var rawOrigTP = sessionLookup.rawOrigTP;
    var rawPtr = sessionLookup.rawPtr;
    var descsPtr = sessionLookup.descsPtr;

    sessionLookup.hasChanges = vrtxEditor.htmlFacade.htmlToJson(this.isMedisin, sessionElms, sessionId, descsPtr, rawOrig, rawOrigTP, rawPtr);
  };
  this.saved = function(isSaveView) {
    for(var type in this.sessionsLookup) {
      for(var session in this.sessionsLookup[type]) {
        var sessionObj = this.sessionsLookup[type][session];
        if(sessionObj.hasChanges) {
          sessionObj.rawOrig = sessionObj.rawPtr; // Copy over Ptr to Orig
          sessionObj.hasChanges = false;
        }
      }
    }
    this.sessionOnlyWindowClose(isSaveView);
  };
  this.sessionOnlyWindowClose = function(isSaveView) {
    if(onlySessionId && isSaveView) {
      var returnUrlToView = $("#global-menu-leave-admin a").attr("href");
      if(this.returnSectionHash.length > 1 && (/safari/.test(vrtxAdmin.ua) && !/chrome/.test(vrtxAdmin.ua))) { // Safari
        var param = (returnUrlToView.indexOf("?") !== -1) ? "&" : "?";
        returnUrlToView += param + "hash=" + this.returnSectionHash.substring(1) + this.returnSectionHash;
      } else {
        returnUrlToView += this.returnSectionHash;
      }
      window.location.href = returnUrlToView;
    }
  };

  var contents = $("#contents");
  if(onlySessionId) contents.find("#vrtx-editor-title-submit-buttons").hide();

  this.loadingUpdate(this.i18n.loadingRetrievingData);
  var cs = this;
  
  // GET JSON
  var retrievedScheduleDeferred = $.Deferred();
  vrtxAdmin.serverFacade.getJSON(url, {
    success: function(data, xhr, textStatus) {
      cs.retrievedScheduleData = data;
      cs.loadingUpdate(cs.i18n.loadingGenerating);
      retrievedScheduleDeferred.resolve();
    },
    error: function(xhr, textStatus) {
      if(textStatus === "parsererror") { // Running VTK or invalid JSON
        retrievedScheduleDeferred.resolve();
        vrtxAdmin.displayErrorMsg(textStatus);
      }
    }
  }, false);
  
  // Setup interaction handlers
  contents.on("click", ".create-fixed-resources-folder", function(e) { // Create Fixed resource folders
    createFixedResourcesFolders(cs, this, baseUrl);
    e.stopPropagation();
    e.preventDefault();    
  });
  contents.on("click", "input[name='vrtxStatus']", function(e) { // Change Cancelled Status
    changeStatusTitle(cs, this, true);
    e.stopPropagation();
  });
  contents.on("keyup", "input[name='vrtxTitle']", $.debounce(50, true, function () { // Change Title
    changeStatusTitle(cs, this, false);
  }));
  
  var editorProperties = vrtxEditor.editorForm.find(".properties");
  editorProperties.hide();
  
  // Initialize Multiple fields
  initMultipleInputFields();

  // When Schedule data and Multiple fields templates has been retrieved
  $.when(retrievedScheduleDeferred, vrtxEditor.multipleFieldsBoxesDeferred).done(function() {
    var csRef = cs;
    
    if(csRef.retrievedScheduleData == null) {
      csRef.loadingUpdate("");
      editorProperties.prepend("<p>" + csRef.i18n.noData + "</p>");
      return;
    }
    $(".vrtx-json").remove(); // Remove - TODO: don't generate

    // Is it a medisin course
    csRef.isMedisin = typeof csRef.retrievedScheduleData.vrtxResourcesFixedUrl === "string";
    csRef.vrtxResourcesFixedUrl = csRef.retrievedScheduleData.vrtxResourcesFixedUrl;
    delete csRef.retrievedScheduleData.vrtxResourcesFixedUrl;
      
    /*
     * Single session mode
     */
    if(onlySessionId) {
      onlySessionId = decodeURIComponent(onlySessionId);
      var sessionOnly = csRef.getSessionOnlyHtml(onlySessionId);
      var html = !sessionOnly ? "<p>" + csRef.i18n.noSessionData + "</p>" : sessionOnly.html;

      contents.find("#vrtx-editor-title-submit-buttons-inner-wrapper > h2")
              .html(csRef.i18n.editOnlySessionTitle + "<a href='javascript:void(0)' class='vrtx-close-dialog-editor'></a>");
      document.title = csRef.i18n.editOnlySessionTitle;
      
      var editorSubmitButtons = vrtxEditor.editorForm.find(".submitButtons");

      if(sessionOnly) {
        editorProperties.prepend("<h4 class='property-label'>" + sessionOnly.title + "</h4>" + html);
        csRef.enhanceSession("single", "one", editorProperties);
        var newButtonsHtml = "<button class='vrtx-button vrtx-embedded-button' id='vrtx-embedded-save-view-button'>" + csRef.i18n.saveView + "</button>" +
                             "<button class='vrtx-focus-button vrtx-embedded-button' id='vrtx-embedded-save-button'>" + csRef.i18n.save + "</button>" +
                             "<button class='vrtx-button vrtx-embedded-button' id='vrtx-embedded-cancel-button'>" + csRef.i18n.cancel + "</button>";
                             
        editorSubmitButtons.on("click", "#vrtx-embedded-save-view-button", function(e) { /* Save and view shortcut */
          editorSubmitButtons.find("#saveAndViewButton").trigger("click");
          e.stopPropagation();
          e.preventDefault();
        });
        editorSubmitButtons.on("click", "#vrtx-embedded-save-button", function(e) { /* Save shortcut */
          editorSubmitButtons.find("#updateAction").trigger("click");
          e.stopPropagation();
          e.preventDefault();
        });
      } else {
        editorProperties.prepend(html);
        var newButtonsHtml = "<button class='vrtx-button vrtx-embedded-button' id='vrtx-embedded-cancel-button'>" + csRef.i18n.cancel + "</button>";
      }
      editorSubmitButtons.prepend(newButtonsHtml);
      contents.find("#vrtx-editor-title-submit-buttons").show();
      
      /* Cancel and unlock to view */
      editorSubmitButtons.on("click", "#vrtx-embedded-cancel-button", function(e) {
        var form = $("form[name='unlockForm']");
        var url = form.attr("action");
        var dataString = form.serialize();
        vrtxAdmin.serverFacade.postHtml(url, dataString, {
          success: function (results, status, resp) {
            csRef.cancelled = true;
            csRef.sessionOnlyWindowClose(true);
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
      
    /*
     * All sessions mode
     */
    } else {
      var html = "<div class='accordion-title'>" + csRef.i18n.titles.plenary + "</div>" +
                 csRef.getActivitiesForTypeHtml("plenary", true) +
                 "<div class='accordion-title'>" + csRef.i18n.titles.group + "</div>" +
                 csRef.getActivitiesForTypeHtml("group", false);
      editorProperties.prepend("<div class='vrtx-grouped'>" + html + "</div>"); 
      setupFullEditorAccordions(csRef, editorProperties);
      
      /* Cancel and unlock to admin preview - could also just click the button for unlock */
      $(document).on("click", "#cancelAction", function(e) {
        var form = $("form[name='unlockForm']");
        var url = form.attr("action");
        var dataString = form.serialize();
        vrtxAdmin.serverFacade.postHtml(url, dataString, {
          success: function (results, status, resp) {
            csRef.cancelled = true;
            window.location.href = window.location.pathname + "?vrtx=admin";
          }
        });
        e.stopPropagation();
        e.preventDefault();
      });
    }
    
    JSON_ELEMENTS_INITIALIZED.resolve();
    
    var waitALittle = setTimeout(function() {      
      csRef.loadingUpdate("");
      editorProperties.show();
    }, 50);
  });
}

/*
 * Creates fixed resources folder (if not exists) and subfolder (fixed or semester)
 */
function createFixedResourcesFolders(cs, elm, baseUrl) {
  var linkElm = $(elm);
  var sessionId = linkElm[0].id.split("create-fixed-resources-folder-")[1];

  var hasParentFolder = false;
    
  var splitA = sessionId.split("SID");
  var id = splitA[0];
  sessionId = splitA[1];
  var splitB = sessionId.split("SUBF");
  sessionId = splitB[0];
    
  var session = cs.sessionsLookup[id][sessionId];

  var subfolder = splitB[1];
  if(subfolder.indexOf("PARENTR") !== -1) {
    var splitC = subfolder.split("PARENTR");
    subfolder = splitC[0];
    var collectionUrl = decodeURIComponent(splitC[1]);

    hasParentFolder = true;
  } else {
    var sessionDisciplines = session.rawOrigTP.disciplines;
    var sessionTitle = session.rawPtr.vrtxTitle || session.rawOrigTP.title;
    var sequenceId = session.sequenceId;

    var collectionTitle = (sessionDisciplines ? sessionDisciplines.join(", ") + " - " : "") + sessionTitle + " - " + sequenceId;
    var collectionName = vrtxAdmin.inputUpdateEngine.substitute((sessionDisciplines ? sessionDisciplines.join("-") + "-" : "") + sessionTitle + "-" + sequenceId, false);

    var collectionBaseUrl = cs.vrtxResourcesFixedUrl;
    if(!/\/$/.test(collectionBaseUrl)) { // Add last '/' if missing
      collectionBaseUrl += "/";
    }
    var collectionUrl = collectionBaseUrl + collectionName;
  }

  // POST Create fixed resources subfolder
  var createFixedResourceSubfolder = function(form, csrf) {
    var dataString = "uri=" + encodeURIComponent(collectionUrl + "/" + subfolder) +
                     addCreateProperty("", "userTitle", (cs.i18n[subfolder] || subfolder)) +
                     "&csrf-prevention-token=" + csrf;
    vrtxAdmin.serverFacade.postHtml(form.attr("action"), dataString, {
      success: function (results, status, resp) {
        linkElm.hide();
        linkElm.next().hide();
        $("<iframe class='admin-fixed-resources-iframe' src='" + collectionUrl + "/" + subfolder + cs.embeddedAdminService + "&upload=true' frameborder='0'></iframe>").insertAfter(linkElm);
      },
      error: function (xhr, textStatus) {
        if(xhr.status === 500) { // 500 means created but has cached stuff
          $(".errormessage.message").remove();
          linkElm.hide();
          linkElm.next().hide();
          $("<iframe class='admin-fixed-resources-iframe' src='" + collectionUrl + "/" + subfolder + cs.embeddedAdminService + "&upload=true' frameborder='0'></iframe>").insertAfter(linkElm);
        } else {
          var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, true);
          vrtxAdmin.displayErrorMsg(msg);
        }
      }
    });
  };
  
  // POST Create fixed resources folder
  var createFixedResourceFolder = function(form, csrf) {
    var dataString = "uri=" + encodeURIComponent(collectionUrl) +
                     "&type=fixed-resources-collection" +
                      addCreateProperty("http://www.uio.no/navigation", "userTitle", collectionTitle) +
                      addCreateProperty("http://www.uio.no/resource-types/fixed-resources-collection", "fixed-resources-codes", sequenceId);
    // Disciplines if exists
    if(sessionDisciplines) {
      for(var i = 0, len = sessionDisciplines.length; i < len; i++) {
        dataString += addCreateProperty("", "tags", sessionDisciplines[i]);
      }
    }
    // Hide folder from navigation
    dataString += addCreateProperty("http://www.uio.no/navigation", "hidden", "true") +
                  "&csrf-prevention-token=" + csrf;
    vrtxAdmin.serverFacade.postHtml(form.attr("action"), dataString, {
      success: function (results, status, resp) {
        createFixedResourceSubfolder(form, csrf);
      }
    });
  };
    
  // GET create form
  vrtxAdmin.serverFacade.getHtml(baseUrl + "?vrtx=admin&service=create-collection-with-properties", {
    success: function (results, status, resp) {
      var form = $($.parseHTML(results)).find("#create-collection-form");
      var csrf = form.find("input[name='csrf-prevention-token']").val();
        
      if(hasParentFolder) {
        createFixedResourceSubfolder(form, csrf);
      } else {
        vrtxAdmin.serverFacade.head(collectionUrl, {
          success: function (results, status, resp) { // Top folder exists: CREATE subfolder
            createFixedResourceSubfolder(form, csrf);
          },
          error: function (xhr, textStatus, errMsg) { // Top folder not exists: CREATE folder and then subfolder
            if(xhr.status == 404) {
              createFixedResourceFolder(form, csrf);
            } else {
              var msg = vrtxAdmin.serverFacade.error(xhr, textStatus, true);
              vrtxAdmin.displayErrorMsg(msg);
            }
          }
        });
      }
    }
  });
}

/*
 * Add create property
 */
function addCreateProperty(ns, name, val) {
  return "&propertyNamespace%5B%5D=" + encodeURIComponent(ns) +
         "&propertyName%5B%5D=" + name +
         "&propertyValue%5B%5D=" + encodeURIComponent(val);
}

/*
 * Updates session title text on status and title changes
 */
function changeStatusTitle(cs, elm, isStatus) {
  var formElm = $(elm);
  var content = formElm.closest(onlySessionId ? ".properties" : ".accordion-wrapper");
  var titleElm = content.find(onlySessionId ? ".property-label > .session-title" : "> .header > .session-title");
  
  var newTitle = content.find("input[name='vrtxTitle']");
  var hasNewTitle = newTitle.length && newTitle.val() != "";
  var origTitle = decodeURI(titleElm.attr("data-orig"));
  if(isStatus) {
    titleElm.html((formElm[0].checked ? "<span class='header-status'>" + cs.i18n.cancelled + "</span> - " : "") +
                  "<span class='header-title'>" + (hasNewTitle ? newTitle.val() : origTitle) + "</span>");
  } else {
    titleElm.find(".header-title").html(hasNewTitle ? newTitle.val() : origTitle);
  }
}

/*
 * Setup accordions for full editor
 *
 * Number of accordions levels/tiers is:
 *  - 2 for plenary
 *  - 3 for groups
 *
 */
function setupFullEditorAccordions(csRef, editorProperties) {
  var animationSpeed = 200;

  // Tier 3
  var accordionOnActivateTier3 = function (id, e, ui, accordion) {
    if(ui.newHeader[0]) { // Enhance multiple fields in session on open
      var sessionId = ui.newHeader[0].id;
      var sessionElm = $(ui.newHeader).closest("div");
      var content = sessionElm.find("> .accordion-content");
    
      csRef.lastId = id;
      csRef.lastSessionId = sessionId;
      csRef.lastElm = content;
      
      csRef.enhanceSession(id, sessionId, content);
    }
    if(ui.oldHeader[0]) { // Update session and accordion title on close
      var sessionId = ui.oldHeader[0].id;
      var sessionElm = $(ui.oldHeader).closest("div");
      var content = sessionElm.find("> .accordion-content");
      
      csRef.saveSession(content, id, sessionId);
    }
  };  

  // Tier 2
  var accordionOnActivateTier2 = function (id, isTier1, e, ui, accordion) {
    if(isTier1) {
      accordionOnActivateTier3(id, e, ui, accordion);
    } else {
      if(ui.newHeader[0]) {
        var contentWrp = $(ui.newHeader[0]).parent().find(".accordion-content");
        var optsH5 = {
          elem: contentWrp.find(".vrtx-grouped"),
          headerSelector: "h5",
          onActivate: function (e, ui, accordion) {
            accordionOnActivateTier3(id, e, ui, accordion);
          },
          animationSpeed: animationSpeed
        };
        var accH5 = new VrtxAccordion(optsH5);
        accH5.create();
        optsH5.elem.addClass("fast");
      }
    }
  };
  
  // Tier 1
  var accordionOnActivateTier1 = function (isTier1, e, ui, accordion) {
    if(ui.newHeader[0]) {
      var id = ui.newHeader[0].id;
      var contentWrp = $("#" + id).parent().find(".accordion-content");
      if(isTier1) { // Lookup and add sessions HTML to DOM
        if(!contentWrp.children().length) { // If not already added
          contentWrp.html("<div class='vrtx-grouped'>" + csRef.sessionsLookup[id].html + "</div>");
        }
      }
      var optsH4 = {
        elem: contentWrp.find(".vrtx-grouped"),
        headerSelector: "h4",
        onActivate: function (e, ui, accordion) {
          if(!isTier1 && ui.newHeader[0]) { // Lookup and add sessions HTML to DOM
            id = ui.newHeader[0].id;
            var contentWrp = $("#" + id).parent().find(".accordion-content");
            if(!contentWrp.children().length) { // If not already added
              contentWrp.html("<div class='vrtx-grouped'>" + csRef.sessionsLookup[id].html + "</div>");
            }
          }
          accordionOnActivateTier2(id, isTier1, e, ui, accordion);
        },
        animationSpeed: animationSpeed
      };
      var accH4 = new VrtxAccordion(optsH4);
      accH4.create();
      optsH4.elem.addClass("fast");
    }
  };

  // Tier 0
  var optsH3 = {
    elem: editorProperties.find("> .vrtx-grouped"),
    headerSelector: "h3",
    onActivate: function (e, ui, accordion) {
      if(ui.newHeader[0]) {
        var ident = $(ui.newHeader[0]).closest(".accordion-wrapper");
        accordionOnActivateTier1(ident.hasClass("skip-tier"), e, ui, accordion);
      }
    },
    animationSpeed: animationSpeed
  };
  var accH3 = new VrtxAccordion(optsH3);
  accH3.create();
  optsH3.elem.addClass("fast");
}
