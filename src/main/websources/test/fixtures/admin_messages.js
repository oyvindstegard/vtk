vrtxAdmin.messages = {
  upload: {
    inprogress: '${vrtx.getMsg("uploading.in-progress")}',
    processes: '${vrtx.getMsg("uploading.processes")}',
    existing: {
      title: '${vrtx.getMsg("uploading.existing.title")}',
      skip: '${vrtx.getMsg("uploading.existing.skip")}',
      overwrite: '${vrtx.getMsg("uploading.existing.overwrite")}'
    }
  },
  deleting: {
    inprogress: '${vrtx.getMsg("deleting.in-progress")}'
  },
  move: {
    existing: {
      sameFolder: "${vrtx.getMsg("move.existing.same-folder")}"
    }
  },
  publish: {
    unpublishDateBefore: '${vrtx.getMsg("publishing.edit.invalid.unpublishDateBefore")}',
    unpublishDateNonExisting: '${vrtx.getMsg("publishing.edit.invalid.unpublishDateNonExisting")}'
  },
  courseSchedule: {
    updated: '${vrtx.getMsg("course-schedule.edit.updated")}',
    updatedTitle: '${vrtx.getMsg("course-schedule.edit.updated.title")}'
  },
  dropdowns: {
    createTitle: '${vrtx.getMsg("dropdowns.create.title")}',
    resourceTitle: '${vrtx.getMsg("dropdowns.resource.title")}',
    editorTitle: '${vrtx.getMsg("dropdowns.editor.title")}',
    publishingTitle: '${vrtx.getMsg("dropdowns.publishing.title")}'
  },
  oldImageContainers: {
    convert: {
      title: '${vrtx.getMsg("editor.old-image-containers.convert.title")}',
      msg: '${vrtx.getMsg("editor.old-image-containers.convert.msg")}',
      ok: '${vrtx.getMsg("editor.old-image-containers.convert.ok")}',
      cancel: '${vrtx.getMsg("editor.old-image-containers.convert.cancel")}'
    },
    notAllConverted: {
      title: '${vrtx.getMsg("editor.old-image-containers.not-all-converted.title")}',
      msg: '${vrtx.getMsg("editor.old-image-containers.not-all-converted.msg")}'
    }
  },
  system: {
    goingDown: {
      title: '${vrtx.getMsg("system.going-down.title")}',
      msg: '${vrtx.getMsg("system.going-down.msg")}'
    }
  },
  editor: {
    timedOut: {
      title: '${vrtx.getMsg("editor.timed-out.title")}',
      msg: '${vrtx.getMsg("editor.timed-out.msg")}',
      ok: '${vrtx.getMsg("editor.timed-out.ok")}'
    }
  }
}

vrtxAdmin.serverFacade.errorMessages = {
  title: "${vrtx.getMsg('ajaxError.title')}",
  retryTitle: "${vrtx.getMsg('ajaxError.retry.title')}",
  retryOK: "${vrtx.getMsg('ajaxError.retry.ok')}",
  retryCancel: "${vrtx.getMsg('ajaxError.retry.cancel')}",
  general: "${vrtx.getMsg('ajaxError.general')}",
  timeout: "${vrtx.getMsg('ajaxError.timeout')}",
  abort: "${vrtx.getMsg('ajaxError.abort')}",
  parsererror: "${vrtx.getMsg('ajaxError.parsererror')}",
  offline: "${vrtx.getMsg('ajaxError.offline')}",
  lockStolen: "${vrtx.getMsg('ajaxError.lockStolen')}",
  lockStolenTitle: "${vrtx.getMsg('ajaxError.lockStolen.title')}",
  lockStolenOk: "${vrtx.getMsg('ajaxError.lockStolen.ok')}",
  outOfDate: "${vrtx.getMsg('ajaxError.out-of-date')}",
  outOfDateTitle: "${vrtx.getMsg('ajaxError.out-of-date.title')}",
  outOfDateOk: "${vrtx.getMsg('ajaxError.out-of-date.ok')}",
  cantBackupFolderTitle: "${vrtx.getMsg('ajaxError.cant-backup-folder.title')}",
  cantBackupFolder: "${vrtx.getMsg('ajaxError.cant-backup-folder')}",
  uploadingFilesFailedTitle: "${vrtx.getMsg('ajaxError.uploading-files.title')}",
  uploadingFilesFailed: "${vrtx.getMsg('ajaxError.uploading-files')}",
  sessionInvalidOk: "${vrtx.getMsg('ajaxError.sessionInvalid.ok')}",
  sessionInvalidOkInfo: "${vrtx.getMsg('ajaxError.sessionInvalid.ok.info')}",
  sessionWaitReauthenticate: "${vrtx.getMsg('ajaxError.sessionInvalid.waitReauthenticate')}",
  sessionValidatedTitle: "${vrtx.getMsg('ajaxError.sessionValidated.title')}",
  sessionInvalidSave: "${vrtx.getMsg('ajaxError.sessionInvalid.save')}",
  sessionInvalidTitleSave: "${vrtx.getMsg('ajaxError.sessionInvalid.save.title')}",
  sessionValidatedSave: "${vrtx.getMsg('ajaxError.sessionValidated.save')}",
  sessionValidatedOkSave: "${vrtx.getMsg('ajaxError.sessionValidated.save.ok')}",
  sessionInvalid: "${vrtx.getMsg('ajaxError.sessionInvalid')}",
  sessionInvalidTitle: "${vrtx.getMsg('ajaxError.sessionInvalid.title')}",
  sessionValidated: "${vrtx.getMsg('ajaxError.sessionValidated')}",
  sessionValidatedOk: "${vrtx.getMsg('ajaxError.sessionValidated.ok')}",
  sessionValidatedFailed: "${vrtx.getMsg('ajaxError.sessionValidatedFailed')}",
  sessionValidatedFailedOk: "${vrtx.getMsg('ajaxError.sessionValidatedFailed.ok')}",
  down: "${vrtx.getMsg('ajaxError.down')}",
  s500: "${vrtx.getMsg('ajaxError.s500')}",
  s400: "${vrtx.getMsg('ajaxError.s400')}",
  s401: "${vrtx.getMsg('ajaxError.s401')}",
  s403: "${vrtx.getMsg('ajaxError.s403')}",
  s404: "${vrtx.getMsg('ajaxError.s404')}",
  s423: "${vrtx.getMsg('ajaxError.s423')}",
  s4233: "${vrtx.getMsg('ajaxError.s423.parent')}",
  customTitle: {
    "0": "${vrtx.getMsg('ajaxError.offline.title')}",
    "4233": "${vrtx.getMsg('ajaxError.s423.parent.title')}"
  }
};
